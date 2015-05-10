JFLAGS = -g
JC = javac
.SUFFIXES: .java .class
.java.class:
		$(JC) $(JFLAGS) $*.java

CLASSES = \
        BFclient.java \
        Message.java \
        MessageReceiver.java \
        MessageReceiverWorker.java \
        UpdateSender.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
		$(RM) *.class