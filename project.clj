(defproject appengine-magic "0.2.2-SNAPSHOT"
  :description "Google App Engine library for Clojure."
  :repositories {"maven-gae-plugin" "http://maven-gae-plugin.googlecode.com/svn/repository"}
  :namespaces [appengine-magic.core appengine-magic.servlet appengine-magic.utils appengine-magic.testing]
  :dependencies [[org.clojure/clojure "1.3.0-SNAPSHOT"]
                 [ring/ring-core "0.3.2"]
                 [com.google.appengine/appengine-api-1.0-sdk "1.3.8"]
                 [com.google.appengine/appengine-api-labs "1.3.8"]
                 [com.google.appengine/appengine-api-stubs "1.3.8"]
                 [com.google.appengine/appengine-testing "1.3.8"]
                 [com.google.appengine/appengine-local-runtime "1.3.7"]
                 [com.google.appengine/appengine-tools-api "1.3.7"]]
  :dev-dependencies [[swank-clojure "1.2.1"]])
