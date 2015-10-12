(ns jakebasile.dr-datomic.refs
  (:require [datomic.api :as d]))

(defn create-local-db
  []
  ;; We'll create a new in-memory database for these examples.
  (d/create-database "datomic:mem://refs"))

(defn get-connection
  []
  (d/connect "datomic:mem://refs"))

(defn get-db
  []
  (d/db (get-connection)))

(defn add-schema
  []
  (let [name-attr {;; This is unchanged from before.
                   :db/ident :person/name
                   :db/id (d/tempid :db.part/db)
                   :db/valueType :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db.install/_attribute :db.part/db}
        pet-attr {;; Now we'll add a ref attribute.
                  :db/ident :person/pets
                  :db/id (d/tempid :db.part/db)
                  :db/valueType :db.type/ref
                  ;; a ref has cardinality just like any other attribute.
                  ;; A person can have many (or too many) pets.
                  :db/cardinality :db.cardinality/many
                  :db.install/_attribute :db.part/db}
        pet-name {;; Now we need to define an attribute for the pet.
                  :db/ident :pet/name
                  :db/id (d/tempid :db.part/db)
                  :db/valueType :db.type/string
                  :db/cardinality :db.cardinality/one
                  :db.install/_attribute :db.part/db}
        conn (get-connection)]
    ;; commit the schema
    (d/transact conn [name-attr pet-attr pet-name])))

(defn add-person-and-pet
  [person-name pet-name]
  (let [pet {;; now we'll make an entity for this person's pet.
             :db/id (d/tempid :db.part/user)
             :pet/name pet-name}
        person {;; just like before, we'll use a map to define our person entity.
                :db/id (d/tempid :db.part/user)
                :person/name person-name
                ;; To add the pet above as a reference from this person, we can use the tempid
                ;; created above. remember, this only works within a single transaction.
                :person/pets (:db/id pet)}
        conn (get-connection)]
    ;; commit the transaction. Note that order doesn't matter within the transaction vector.
    (d/transact conn [person pet])))


(defn add-person-and-pets
  [person-name & pet-names]
  ;; You can also add many entities in one transaction.
  (let [person {:db/id (d/tempid :db.part/user)
                :person/name person-name}
         pets (for [pet-name pet-names]
                {:db/id (d/tempid :db.part/user)
                 :pet/name pet-name
                 ;; And here's the neat part. You can use the reverse reference mechanic,
                 ;; the _, to add a reference from the person to this entity. This works
                 ;; not only on entity creation but can be used with an entity map object!
                 ;; you've seen this earlier in the :db.install/_attribute mechanic in schema
                 ;; definition.
                 :person/_pets (:db/id person)})
        conn (get-connection)]
    (d/transact conn (conj pets person))))

(defn find-person
  [person-name]
  (let [q {:find '[?person .]
           :in '[$ ?name]
           :where '[[?person :person/name ?name]]}
        db (get-db)]
    (when-let [person-id (d/q q db person-name)]
      (d/entity db person-id))))

(defn find-pet
  [pet-name]
  (let [q {:find '[?pet .]
           :in '[$ ?name]
           :where '[[?pet :pet/name ?name]]}
        db (get-db)]
    (when-let [pet-id (d/q q db pet-name)]
      (d/entity db pet-id))))

(defn find-owners
  [pet]
  (let [owners (:person/_pets pet)]
    (map :person/name owners)))


