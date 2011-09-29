(ns appengine-magic.services.mail
  (:refer-clojure :exclude (send))
  (:use [appengine-magic.utils :only [record copy-stream]])
  (:import [com.google.appengine.api.mail MailServiceFactory
            MailService$Message MailService$Attachment]))


(defonce ^:dynamic *mail-service* (atom nil))


(defn get-mail-service []
  (when (nil? @*mail-service*)
    (reset! *mail-service* (MailServiceFactory/getMailService)))
  @*mail-service*)


(defrecord MailMessage [from to subject text-body html-body
                        sent-date received-date
                        reply-to cc bcc attachments message-id])


(defn make-attachment [filename data]
  (MailService$Attachment. filename (if (instance? (class (byte-array 0)) data)
                                        data
                                        (.getBytes data))))


(defn make-message [& {:keys [from to subject text-body html-body
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
    (record MailMessage
            :from from :to to :subject subject
            :text-body text-body :html-body html-body
            :reply-to reply-to :cc cc :bcc bcc
            :attachments attachments)))


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


(defrecord #^{:private true} MessageAlternativeParts [parts])
(defrecord #^{:private true} MessagePart [filename content-type data])


(defn- deconstruct-message [#^javax.mail.internet.MimeMessage message]
  (let [all-subparts (fn [part]
                       (map #(.getBodyPart part %) (range (.getCount part))))
        subparts (fn subparts [part]
                   (let [content-type (.toLowerCase (.getContentType part))]
                     (cond
                      ;; multiple attachments
                      (re-matches #"multipart\/mixed.*" content-type)
                      (doall (map subparts (all-subparts (.getContent part))))
                      ;; probably message text
                      (re-matches #"multipart\/alternative.*" content-type)
                      (MessageAlternativeParts.
                       (doall (map subparts (all-subparts (.getContent part)))))
                      ;; a specific attachment
                      :else (let [content (.getContent part)]
                              (if (string? content)
                                  (MessagePart. (.getFileName part) content-type content)
                                  (with-open [tempout (java.io.ByteArrayOutputStream.)]
                                    (copy-stream (.getInputStream part) tempout)
                                    (MessagePart. (.getFileName part) content-type
                                                  (.toByteArray tempout))))))))]
    (flatten [(subparts message)])))


(defn parse-message [request]
  (let [session (javax.mail.Session/getDefaultInstance (java.util.Properties.) nil)
        raw-msg (javax.mail.internet.MimeMessage. session (.getInputStream (:request request)))
        from (first (map #(.toString %) (.getFrom raw-msg)))
        subject (.getSubject raw-msg)
        sent-date (.getSentDate raw-msg)
        received-date (.getReceivedDate raw-msg)
        to (map #(.toString %) (.getRecipients raw-msg javax.mail.Message$RecipientType/TO))
        cc (map #(.toString %) (.getRecipients raw-msg javax.mail.Message$RecipientType/CC))
        reply-to (first (map #(.toString %) (.getReplyTo raw-msg)))
        message-id (.getMessageID raw-msg)
        msg-raw-parts (deconstruct-message raw-msg)
        msg-parts (reduce (fn breakdown [acc raw-part]
                            (cond
                             ;; alternatives
                             (instance? MessageAlternativeParts raw-part)
                             (reduce breakdown acc (:parts raw-part))
                             ;; has a valid filename, must be an attachment
                             (not (nil? (:filename raw-part)))
                             (assoc acc :attachments
                                    (conj (:attachments acc)
                                          (make-attachment (:filename raw-part)
                                                           (:data raw-part))))
                             ;; text part
                             (re-matches #"text\/plain.*" (:content-type raw-part))
                             (assoc acc :text-body (:data raw-part))
                             ;; HTML part
                             (re-matches #"text\/html.*" (:content-type raw-part))
                             (assoc acc :html-body (:data raw-part))))
                          {:attachments []}
                          msg-raw-parts)]
    (record MailMessage
            :from from :to to :subject subject
            :reply-to reply-to :cc cc
            :text-body (:text-body msg-parts) :html-body (:html-body msg-parts)
            :attachments (:attachments msg-parts)
            :message-id message-id)))
