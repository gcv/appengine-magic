The appengine-magic library attempts to abstract away the nuts and bolts of
bootstrapping a Google App Engine application. The application should just
depend on appengine-magic, and it only needs to put its resources into the
resources/war/WEB-INF directory. It also needs to provide the .xml descriptor
files for the application. Beyond that, however, it can use any framework to
make a Ring-compatible handler, and use appengine-magic to either manage a local
development server, or prepare for App Engine deployment.
