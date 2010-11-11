(ns appengine-magic.services.mail
  (:refer-clojure :exclude (send))
  (:import [com.google.appengine.api.mail MailServiceFactory
            MailService$Message MailService$Attachment]))


(defonce *mail-service* (atom nil))


(defn get-mail-service []
  (when (nil? @*mail-service*)
    (reset! *mail-service* (MailServiceFactory/getMailService)))
  @*mail-service*)


(defrecord MailMessage [from to subject text-body html-body
                        reply-to cc bcc attachments])


(defn make-attachment [filename data]
  (MailService$Attachment. filename data))


(defn make-mail-message [& {:keys [from to subject text-body html-body
                                   reply-to cc bcc attachments]
                            :or {text-body "", html-body "", cc [], bcc [], attachments []}}]
  ;; normalize and error-check
  (let [from (if-not (nil? from)
                 from
                 (throw (IllegalArgumentException. ":from argument required")))
        to (cond
            ;; not given
            (nil? to)
            (throw (IllegalArgumentException. ":to argument required"))
            ;; normalize for multiple recipients
            (sequential? to)
            to
            :else [to])
        subject (if-not (nil? subject)
                    subject
                    (throw (IllegalArgumentException. ":subject argument required")))
        cc (if (not (or (nil? cc) (sequential? cc))) [cc] cc)
        bcc (if (not (or (nil? bcc) (sequential? bcc))) [bcc] bcc)]
    (MailMessage. from to subject text-body html-body reply-to cc bcc attachments)))


(defn- make-mail-service-message [^MailMessage msg]
  (doto (MailService$Message.)
    (.setSender (:from msg))
    (.setTo (:to msg))
    (.setSubject (:subject msg))
    (.setTextBody (:text-body msg))
    (.setHtmlBody (:html-body msg))
    (.setReplyTo (:reply-to msg))
    (.setCc (:cc msg))
    (.setBcc (:bcc msg))
    (.setAttachments (:attachments msg))))


(defn send [^MailMessage msg]
  (.send (get-mail-service) (make-mail-service-message msg)))


(defn send-to-admins [^MailMessage msg]
  (.sendToAdmins (get-mail-service) (make-mail-service-message msg)))
