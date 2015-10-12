(ns jakebasile.dr-datomic.history
  (:require [datomic.api :as d]))

(defn create-local-db
  []
  ;; We'll create a new in-memory database for these examples.
  (d/create-database "datomic:mem://history"))

(defn get-connection
  []
  (d/connect "datomic:mem://history"))

(defn get-db
  []
  (d/db (get-connection)))

(defn add-schema
  []
  ;; A basic person schema.
  (let [name-attr {:db/ident :person/name
                   :db/id (d/tempid :db.part/db)
                   :db/valueType :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db.install/_attribute :db.part/db}
        email-attr {:db/ident :person/email
                    :db/id (d/tempid :db.part/db)
                    :db/valueType :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db.install/_attribute :db.part/db}
        conn (get-connection)]
    (d/transact conn [name-attr email-attr])))

(defn add-person
  [pname email]
  ;; Add the person in, just as before
  (let [person {:db/id (d/tempid :db.part/user)
                :person/name pname
                :person/email email}
        conn (get-connection)]
    (d/transact conn [person])))

(defn find-person
  [person-name]
  (let [q {:find '[?person .]
           :in '[$ ?name]
           :where '[[?person :person/name ?name]]}
        db (get-db)]
    (when-let [person-id (d/q q db person-name)]
      (d/touch (d/entity db person-id)))))

(defn change-email
  [person email]
  ;; You can assert an attribute on an entity that already exists by using its ID. You can again
  ;; use the shorthand map.
  (let [change {:db/id (:db/id person)
                :person/email email}
        conn (get-connection)]
    (d/transact conn [change])))

(defn get-all-emails
  [person]
  ;; Now we can write a query to get all historical emails for a person.
  (let [q {;; We want to get emails, and we want them in a vector.
           :find '[[?email ...]]
           ;; These are the inputs we'll be sending to the query engine. Usually $ refers to the Database,
           ;; but we have something special in store for it here.
           :in '[$ ?person]
           ;; This is the same type of :where clause as before, but now it's looking at things beyond
           ;; the [entity attribute value] setup. For one, we already know who the ?person is, so we
           ;; need to solve for the other parts of the clause. ?email is what we want, but we want to
           ;; make sure it's been added to the database, which is the final part. the _ could get
           ;; the transaction id that asserted this attribute but for now we are skipping it.
           :where '[[?person :person/email ?email _ true]]}
        db (get-db)
        ;; This gets a historical DB, which contains not just the current value but all
        ;; transactions up to the point in time of the database object you send to it. Think of it
        ;; as the git commit history instead of your working directory.
        hist-db (d/history db)]
    ;; This shows that d/q can query over things that aren't just datomic databases. Handy!
    (d/q q hist-db (:db/id person))))

; => (add-person "John" "john@example.com")
; ...
; => (find-person "John")
; {:db/id 123 :person/name "John" :person/email "john@example.com"}
; => (change-email (find-person "John") "xXxJohn420xXx@example.com")
; ...
; => (get-all-emails (find-person "John"))
; ["xXxjohn420xXx@example.com" "john@example.com"]

