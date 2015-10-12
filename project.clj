(defproject jakebasile/dr-datomic "0.0.1"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [com.datomic/datomic-free "0.9.5302" :exclusions [joda-time]]]
  :repl-options {:init (do (require '[datomic.api :as d]))
                 :init-ns jakebasile.dr-datomic.intro})

