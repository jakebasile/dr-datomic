(ns jakebasile.dr-datomic.intro
  (:require [datomic.api :as d]))

(defn create-local-db
  []
  ;; You can use an in-memory database to play around with Datomic or for testing.
  ;; Obviously, this doesn't persist.
  (d/create-database "datomic:mem://intro"))

(defn get-connection
  []
  (d/connect "datomic:mem://intro"))

(defn get-db
  []
  (d/db (get-connection)))

; => (create-local-db)
; true

(defn add-schema
  []
  ;; Now we can add a basic attribute schema. You can use a Map shorthand to write
  ;; it out in a convenient way, but these are turned into a series of transactions
  ;; when applied. Even the attributes are immutable!
  (let [attr {;; Every attribute needs an ident which is how you refer to it.
              :db/ident :person/name
              ;; You need to assign an ID to everything you store in Datomic.
              ;; As stated above, Attributes are themselves entities, so they need
              ;; an ID. The Transactor assigns canonical IDs, so you can only use
              ;; temporary IDs here. They only work within a single transaction.
              ;;
              ;; Also, we see that an ID is given a partition. This is a way to
              ;; separate and categorize your data if you want to. All attribute
              ;; definitions go to :db.part/db, and you can make any that you want.
              :db/id (d/tempid :db.part/db)
              ;; You need to tell Datomic what type of data to store in this
              ;; attribute. There are a small selection of data types available.
              :db/valueType :db.type/string
              ;; Cardinality says how many attributes of this type a single entity
              ;; may have. This is either "one" or "many".
              :db/cardinality :db.cardinality/one
              ;; Lastly, we need to "install" this attribute. You do so by using
              ;; a reverse reference to the DB entity itself. This is weird, and I'll
              ;; get into it later.
              :db.install/_attribute :db.part/db}
        ;; We'll need to conenct to the transactor to do this.
        conn (get-connection)]
    ;; to add it to the schema, you run it in a transaction.
    (d/transact conn [attr])))

; => (add-schema)
; #object[datomic.promise.....]
;
; Transactions are synchronous on the transactor, but async on the peer.

(defn add-person
  [pname]
  ;; With our attribute added, we can store our first entity!
  (let [ent {;; You can again use a map shorthand to write an entity. You use
             ;; attribute idents as the keys, and values as ... values.
             ;;
             ;; as before, you always need an ID. This time, we'll store things
             ;; in the user namespace. You shouldn't store anything in the db
             ;; namespace aside from configuration.
             :db/id (d/tempid :db.part/user)
             :person/name pname}
        ;; We'll need a connection, again.
        conn (get-connection)]
    ;; And now we add it in a transaction.
    (d/transact conn [ent])))

; Just as before, transactions are asynchronous.
; => (add-person)
; #object[datomic.promise.....]

; Storing things is cool, but it's much cooler to be able to find them!

(defn find-person-by-name
  [pname]
  ;; Let's do a simple query to find the person we just added.
  (let [q {;; You can write queries as vectors or maps.
           ;; To start, we add what we want to find. Symbols starting with ?
           ;; are variables in the datalog expression. This clause means
           ;; we want to find a single thing and that thing will be referred to
           ;; by ?person.
           ;;
           ;; Not that you must escape some parts of this so that Clojure doesn't
           ;; try to interpret them in this namespace.
           :find '[?person .]
           ;; Next you must say where you want to query. This is where we can add
           ;; parameters such as ?name. $ is special and means "the database". If
           ;; you aren't adding any parameters of your own you don't need to
           ;; include the :in clause.
           :in '[$ ?name]
           ;; Lastly we write the rules for finding the thing(s) we want to find.
           ;; These are evaluated from top to bottom and form a Datalog expression.
           ;; You can even add new intermediate variables in here and call various
           ;; functions that get run on the transactor. But for now, we just want
           ;; to search for a person by name.
           :where '[;; This single clause means find me an entity, called ?person,
                    ;; that has an attribute called :user/name that has the value
                    ;; of ?name. We declared ?name above as a parameter, so it'll
                    ;; be passed in when we query.
                    [?person :person/name ?name]]}
        ;; To query we actually need to get the Database value, not just connect.
        ;; This database object is locked to a point in time and will be consistent
        ;; for its lifetime.
        db (get-db)]
    ;; The first argument to d/q is the query itself. All other arguments come
    ;; afterward. Not that the DB is the first non-query arg to this function, and
    ;; all subsequent args are referred to in the query by the names you declared
    ;; for them in the :in clause above.
    (d/q q db pname)))

; => (find-person-by-name "Buck Turgidson")
; 123456789
;
; Running the query we get back the ID of the entity that matches it. Next, we see
; how to get useful data from that ID.


(defn get-name-from-id
  [id]
  (let [;; If you have an id, you can get the entity it refers to easily.
        ;; This is a map-like data structure that can get you anything stored on
        ;; the entity in question at that point in time. It's lazy, and will only
        ;; talk to the storage service if needed.
        person (d/entity (get-db) id)
        ;; Once you have an entity, you can just use it like a map.
        pname (:person/name person)]
    pname))

