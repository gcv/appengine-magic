(defproject appengine-magic "0.4.6-SNAPSHOT"
  :description "Google App Engine library for Clojure."
  :min-lein-version "1.6.1"
  :repositories {"releases" "http://appengine-magic-mvn.googlecode.com/svn/releases/"
                 "snapshots" "http://appengine-magic-mvn.googlecode.com/svn/snapshots/"}
  :exclusions [org.clojure/clojure]
  :dependencies [[ring/ring-core "0.3.11"]
                 [org.apache.commons/commons-exec "1.1"]
                 ;; App Engine supporting essentials
                 [javax.servlet/servlet-api "2.5"]
                 [commons-io "1.4"]
                 [commons-codec "1.4"]
                 [commons-fileupload "1.2.1"]
                 ;; App Engine administrative interface support
                 [tomcat/jasper-runtime "5.0.28"]
                 [org.apache.geronimo.specs/geronimo-jsp_2.1_spec "1.0.1"]
                 [jstl "1.1.2"] ; repackaged-appengine-jakarta-jstl-1.1.2.jar
                 [taglibs/standard "1.1.2"] ; repackaged-appengine-jakarta-standard-1.1.2.jar
                 [commons-el "1.0"]
                 ;; main App Engine libraries
                 [com.google.appengine/appengine-api-1.0-sdk "1.5.4"]
                 [com.google.appengine/appengine-api-labs "1.5.4"]
                 [com.google.appengine/appengine-api-stubs "1.5.4"]
                 [com.google.appengine/appengine-local-runtime "1.5.4"]
                 [com.google.appengine/appengine-local-runtime-shared "1.5.4"]
                 [com.google.appengine/appengine-testing "1.5.4"]
                 [com.google.appengine/appengine-tools-api "1.5.4"]]
  :dev-dependencies [[org.clojure/clojure "1.2.1"]
                     [swank-clojure "1.3.2"]])
