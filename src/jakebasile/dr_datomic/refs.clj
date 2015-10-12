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
  ;; This is essentially the same as in the intro example, only now I roll in the extra step
  ;; of creating an entity from the result ID.
  (let [q {:find '[?person .]
           :in '[$ ?name]
           :where '[[?person :person/name ?name]]}
        ;; Note that this DB object is used in two places. You can do this to take advantage of caching,
        ;; but be aware that the DB is fixed at a moment in time and will not update with new attributes.
        db (get-db)]
    (when-let [person-id (d/q q db person-name)]
      (d/entity db person-id))))

(defn find-pet
  [pet-name]
  ;; Pets are represented by just another entity instead of a direct attribute.
  ;; You can query for them just like anything else. It is important to note that
  ;; the entity itself is not a "pet", it is an entity that has a "pet/name" attribute.
  (let [q {:find '[?pet .]
           :in '[$ ?name]
           :where '[[?pet :pet/name ?name]]}
        db (get-db)]
    (when-let [pet-id (d/q q db pet-name)]
      (d/entity db pet-id))))

(defn get-pets
  [person]
  ;; You access a reference from the originating entity as either a set of entities or a single entity.
  (let [pets (:person/pets person)]
    (map :pet/name pets)))

; => (get-pets (find-person "Fred"))
; ("Scrappy" "Scooby")

(defn get-owners
  [pet]
  ;; here we see the reverse reference notation again. All references in Datomic are bidirectional,
  ;; so you don't need to do anything special to get them. A side effect of Datomic's flexibility
  ;; is that an entity can be referred to by any number of other entities. In this case, a pet can have
  ;; more than one owner so you will get back a collection of entities that have a :person/pets attribute
  ;; containing this pet.
  (let [owners (:person/_pets pet)]
    (map :person/name owners)))

; => (get-owners (find-pet "Scooby"))
; ("Fred" "Shaggy")

(defn getting-weird
  [person1 person2]
  ;; As I touched on, entities themselves do not have a type. They are simply a place to collect attributes.
  ;; You assign your own meaning to that. In this case, I have assigned that an entity with :pet/name is a pet
  ;; and an entity with person/name is a person and that a person can have many pets.
  ;;
  ;; but datomic doesn't enforce that :person/pets refers to what I consider a pet.
  (let [p1 {:db/id (d/tempid :db.part/user)
            :person/name person1}
        p2 {:db/id (d/tempid :db.part/user)
            :person/name person2}
        rel [:db/add (:db/id p1) :person/pets (:db/id p2)]
        conn (get-connection)]
    (d/transact conn [p1 p2 rel])))

; => (getting-weird "Edward" "Bella")
; ...
; => (map :pet/name (:person/pets (find-person "Edward")))
; (nil)
; => (map :person/name (:person/pets (find-person "Edward")))
; ("Bella")

(defn getting-weirder
  [person-name pet-name]
  ;; Another side effect of entities not having a concrete type and ident namespaces being just a convention
  ;; is that there's nothing stopping you from mixing attributes from different conceptual things.
  (let [petper {:db/id (d/tempid :db.part/user)
                :person/name person-name
                :pet/name pet-name}
        conn (get-connection)]
    (d/transact conn [petper])))

; => (getting-weirder "Picard" "Riker")
; ...
; => (find-person "Picard")
; {:db/id 123}
; => (find-pet "Riker")
; {:db/id 123}
; => (= *1 *2)
; true
; ;; d/touch retrieves all attributes on the given entity.
; => (d/touch (find-person "Picard"))
; {:db/id 123 :person/name "Picard" :pet/name "Riker"}


