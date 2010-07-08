(ns leiningen.appengine-update
  "Update the current Google App Engine application installation."
  (:use appengine-magic.utils)
  (:require leiningen.compile lancet)
  (:import java.io.File))


(defn appengine-update [project]
  (println "deploying App Engine application" (:appengine-display-name project))
  (let [classes-dir *compile-path*
        resources-dir (File. (:resources-path project))
        lib-dir (File. (:library-path project))
        war-dir (File. resources-dir "war")
        WEB-INF-dir (File. war-dir "WEB-INF")
        target-classes-dir (File. WEB-INF-dir "classes")
        target-lib-dir (File. WEB-INF-dir "lib")
        prj-application (:appengine-application project)]
    ;; step 1: compile all
    (leiningen.compile/compile project)
    ;; step 2: copy the compiled app itself (only its namespace)
    (lancet/mkdir {:dir target-classes-dir})
    (lancet/mkdir {:dir target-lib-dir})
    (lancet/copy {:todir (.getPath target-classes-dir)}
                 (lancet/fileset {:dir *compile-path*
                                  :includes (str (dash_ prj-application) "/**")}))
    ;; step 3: copy non-dev dependencies into WEB-INF/lib
    ;; TODO: When Leiningen 1.2 comes out, copy all of lib/ but not lib/dev.
    (lancet/copy {:todir (.getPath target-lib-dir)}
                 (lancet/fileset {:dir lib-dir
                                  :excludes "appengine*"
                                  :excludes "swank*"
                                  :excludes "jetty*"}))
    ;; step 4: call AppCfg "update war"
    ;; ...
    ))
