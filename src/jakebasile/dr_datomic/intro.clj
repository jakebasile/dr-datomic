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

(defn add-basic-schema
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

; => (add-basic-schema)
; #object[datomic.promise.....]
;
; Transactions are synchronous on the transactor, but async on the peer.

(defn add-entity
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
; => (add-entity)
; #object[datomic.promise.....]

