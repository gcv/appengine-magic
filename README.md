The appengine-magic library attempts to abstract away the infrastructural nuts
and bolts of writing a Clojure application for the Google App Engine platform.

The development environment of Google App Engine for Java expects pre-compiled
classes, and generally does not fit well with Clojure's interactive development
model. appengine-magic attempts to make REPL-based development of App Engine
applications as natural as any other Clojure program.

1. Programs using appengine-magic just need to include appengine-magic as a
   Leiningen dev-dependency.
2. appengine-magic takes a Ring handler and makes it available as a servlet for
   App Engine deployment.
3. appengine-magic is also a Leiningen plugin, and adds several tasks which
   simplify preparing for App Engine deployment.

NB: This is a pre-release of the library. appengine-magic applications do not
currently work when deployed to the production App Engine. Please read the
section below about deployment.



## Dependencies

* Clojure 1.2.0
* Leiningen 1.3.1
* Google App Engine SDK 1.3.7
* swank-clojure 1.2.1 (optional)



## Getting Started

### Project setup

You need a copy of the Google App Engine SDK installed
somewhere. appengine-magic cannot replace its `dev_appserver.sh` and `appcfg.sh`
functionality.

1. `lein new` <project-name>
2. `rm src/<project-name>/core.clj` to clean out the default `core.clj` file
   created by Leiningen. 
3. Edit `project.clj`:
   - add `:appengine-application`, a string which should match your project name
   - add `:appengine-display-name`, a free-form string
   - add `:namespaces [<project>.app_servlet]` (or use the equivalent `:aot` directive)
   - add `[appengine-magic "0.1.0-SNAPSHOT"]` to your `dev-dependencies`
4. `lein deps`
5. `lein appengine-new`. This sets up four files for your project: `core.clj`,
   `app_servlet.clj` (the entry point for the application),
   `resources/war/WEB-INF/web.xml` (a servlet descriptor), and
   `resources/war/WEB-INF/appengine-web.xml` (an App Engine application
   descriptor). These files should contain reasonable defaults for your
   application.

The default `.gitignore` file produced by Leiningen works well with the
resulting project, but do take a careful look at it.


### Development process

Launch `lein swank` or `lein repl`, whichever you normally use. Once you have a
working REPL, compile your application's `core.clj`.

The key construct provided by appengine-magic is the
`appengine-magic.core/def-appengine-app` macro. It takes a Ring handler and
defines a new `<project-name>-app` var. (If you want to rename this var,
remember to update `app_servlet.clj`.) That's it: you may now write your
application using any framework which produces a Ring-compatible handler. Then,
just pass this handler to `def-appengine-app`.

To test your work interactively, you can control a Jetty instance from the REPL
using `appengine-magic.core/start` and `appengine-magic.core/stop`. Examples
(assuming you are in your application's `core` namespace and your application is
named `foo`):

    (am/start foo-app)
    (am/stop)
    (am/start foo-app :port 8095)
    (am/stop)

Recompiling the functions which make up your Ring handler should produce
instantaneous results.


### Testing with `dev_appserver.sh`

1. `lein appengine-prepare`. This AOT-compiles the entry point servlet, then
   copies the necessary classes and library dependencies to your application's
   `resources/war/WEB-INF/classes` and `resources/war/WEB-INF/lib` directories.
2. Run `dev_appserver.sh` with a path to your application's `resources/war`
   directory.


### Static resources

Just put all static files into your application's `resources/war` directory. If
you put a file called `index.html` there, it will become a default welcome file.


### Deployment to App Engine

NB: This does not currently work because a dependency of appengine-magic loads
java.net.Socket, a class blacklisted on App Engine. (In spite of this, the
development server provided with the SDK works.)

1. First of all, be careful. You are must manually maintain the version field in
   `appengine-web.xml` and you should understand its implications.
2. `lein appengine-prepare` prepares the `resources/war` directory with the latest
   classes and libraries for deployment.
3. When you are ready to deploy, just run `appcfg.sh update` with a path to your
   application's `resources/war` directory.



## Limitations

As noted above, real deployment to App Engine is pending a solution to the
blacklisted dependency problem.

This is a very early cut of this library. It does not provide access to App
Engine services (the datastore, email sending, authentication, and so
on). Please see [`appengine-clj`](http://github.com/r0man/appengine-clj) for a
library which makes some of those services available in Clojure.

Making App Engine services available requires enumerating the specialized `/_ah`
URLs App Engine uses and figuring out which URL maps to which servlet provided
by the App Engine SDK. Help in doing this work is welcome.



## License

appengine-magic is distributed under the MIT license.