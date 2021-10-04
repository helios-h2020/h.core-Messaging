# HELIOS Messaging API #

## Introduction to functionality ##

HELIOS Messaging API can be used to communicate with other HELIOS
nodes. The implementation includes API calls that can be used
both in publish-subscribe based group communicatios and in one-to-one
direct messaging between the nodes.

Classic messaging applications rely on messaging server that is used
to store and forward messages to clients that are online. Because P2P
messaging does not have such a server node, there are other mechanisms
that must be used to allow clients that come back online to receive the
messages that are sent during the offline period. HELIOS Messaging API
includes a reliable messaging API that is able to resend both group
chat and one-to-one messages to the clients that come back online
after offline period. HELIOS Messaging API includes a message cache
that is used to store recent messages (e.g., max one week old).

HELIOS Messaging API library module **messaging** is an interface library
for different message transport modules. The current implemnetation
is based on js-libp2p.

HELIOS Messaging API is one of the HELIOS Core APIs provided by HELIOS
Communication Manager as highlighted in the picture below:

![HELIOS Messaging API](https://raw.githubusercontent.com/helios-h2020/h.core-Messaging/master/doc/images/helios-messaging.png "Messaging API")


## How to use the library ##

### Introduction ###

The preferred way to use the library is to use reliable messaging with
a class *ReliableHeliosMessagingNodejsLibp2pImpl*. The class supports
messaging in two modes:

* **Publish-subscribe messaging** - Messages are sent (published) to a topic
  and the messages are delivered to all subscribers of the topic.

* **Direct one-to-one messaging** - Messages are only sent between two
  clients.

The clients that have been offline are able to receive messages that
have been sent while they have been offline.

### Create connection ###

Connection is created with class *ReliableHeliosMessagingNodejsLibp2pImpl*
method:

    void connect(HeliosConnectionInfo cinfo, HeliosIdentityInfo identity);

Code example. Assume that `HeliosIdentityInfo identity` is already
set elsewhere and the class has the getApplicationContext method.

    HeliosIdentityInfo identity = ...

    ReliableHeliosMessagingNodejsLibp2pImpl messaging =
        ReliableHeliosMessagingNodejsLibp2pImpl.getInstance();

    messaging.setContext(this.getApplicationContext();
    try {
        messaging.connect(new HeliosConnectionInfo(), identity);
    } catch (HeliosMessagingException e) {
        Log.d(TAG, "Connection failed " + e.toString());
    }

### Subscribing to a topic ###

Subscribe to a topic with a listener (HeliosMessageListener) using class
*ReliableHeliosMessagingNodejsLibp2pImpl* method:

    void subscribe(HeliosTopic topic, HeliosMessageListener listener);

After subscription, all messages published to a topic are also sent to
a subscribed client. The client will receive the messages using a class
implementing the interface *HeliosMessageListener* and a method:

    void showMessage(HeliosTopic topic, HeliosMessage message);

Code example (here we assume that this class also implements the interface
*HeliosMessageListener* as the *this* refrence is passed as a parameter
in a call):

    ReliableHeliosMessagingNodejsLibp2pImpl messaging = ...
    HeliosTopic topic = ...

    try {
        messaging.subscribe(topic, this);
    } catch (HeliosMessagingException e) {
        Log.d(TAG, "Subscription failed " + e.toString());
    }

### Publishing to a topic ###

Publish a message to a topic using class *ReliableHeliosMessagingNodejsLibp2pImpl*
method:

    publish(HeliosTopic topic, String message);

Note that the message string must be JSON string.

Code example:

    ReliableHeliosMessagingNodejsLibp2pImpl messaging = ...
    HeliosTopic topic = ...
    String message = ...

    try {
        messaging.publish(topic, message);
    } catch (HeliosMessagingException e) {
        Log.d(TAG, "Oublishing failed " + e.toString());
    }

### Sending a direct message ###

Send a direct message to another client using class *ReliableHeliosMessagingNodejsLibp2pImpl*
method:

    Future<Unit> sendToFuture(HeliosNetworkAddress address, String protocolId, byte[] data);

Code example using asynchronous Java Future call:

    ReliableHeliosMessagingNodejsLibp2pImpl messaging = ...
    HeliosNetworkAddress address = ...
    String protocolId = ...
    String jsonMsg = ...

    Future<Unit> f = messaging.sendToFuture(address, protocolId, jsonMsg.getBytes());
    f.get(1000, TimeUnit.MILLISECONDS);

### Receiving a direct message ###

The client must first register a listener to a protocol using *HeliosDirectMessaging*
interface implementation inside the class *ReliableHeliosMessagingNodejsLibp2pImpl*.
It is possible to get access to this direct messaging implementation from
*ReliableHeliosMessagingNodejsLibp2pImpl* class using a method:

    HeliosDirectMessaging getDirectMessaging();

The return value is *HeliosDirectMessaging* class instance that can
then be used to set a listener to process received messages:

    void addReceiver(String protocolId, HeliosMessagingReceiver receiver);

Here the parameter *receiver* is a class instance that implements *HeliosMessagingReceiver*
interface, which contains two *receiveMessage* methods that are used to process
incoming direct messages.

Code example assuming that the class implements *HeliosMessagingReceiver*
methods:

    ReliableHeliosMessagingNodejsLibp2pImpl messaging = ...
    String protocolId = ...

    messaging.getDirectMessaging().addReceiver(protocolId, this);

    @Override
    public void receiveMessage(HeliosNetworkAddress address, String protocolId, FileDescriptor fd) {
        ...
    }

    @Override
    public void receiveMessage(HeliosNetworkAddress address, String protocolId, byte[] data) {
        ...
    }

### Remove old messages from internal message cache ###

Because peer-to-peer messaging does not have central message repository, each client
is storing recent messages that can be sent to clients returning back online.
When the client is starting, the messages older than one week are removed from
the internal cache. The class *ReliableHeliosMessagingNodejsLibp2pImpl* has also a
method to explicitly remove all messages that are older than certain threshold
time that is given as a parameter to the method. The *expire* method assumes
that *setContext* mehod has already been called.

    public void expire(ZonedDateTime threshold) throws HeliosMessagingException;

Code example returns all messages older than three days:

    ReliableHeliosMessagingNodejsLibp2pImpl messaging = ...

    try {
        messaging.expire(ZonedDateTime.now().minusDays(3).toInstant().toEpochMilli());
    } catch (HeliosMessagingException e) {
        Log.e(TAG, "Call setContext before expire");
    }

## How to develop ##

An application should include this library by adding it in the build.gradle file
of the application:

    dependencies {
        implementation 'eu.h2020.helios_social.core.messaging:messaging:1.1.11'
    }


The module **messaging** contains interface classes and basic data
structures for messaging that are utilized by messaging protocol
module **messagingnodejslibp2ptest** that implement real messaging
transactions.

Normally HELIOS is using Sonatype Nexus Repository Manager that can be
added to a list of known repositories in a top-level build.gradle file
after well-known Google and JCenter repositories. The prepository
requires username and password that can be stored in a file
$HOME/.gradle/gradle.properties. The following Maven repository
configuration should be added to the top-level build.gradle file
of the application (and the credentials to gradle.properties file):

    allprojects {
        repositories {
            google()
            jcenter()
            maven {
                url "https://builder.helios-social.eu/repository/helios-repository/"
                credentials {
                    username = heliosUser
                    password = heliosPassword
                }
            }
        }

If there is no access to Nexus Repository Manager it is still possible
to specify dependency resolution and refer to local filesystem
versions of the repositories. This might be necessary also for
debugging. The following configuration resolution strategy can be
added to the build.gradle file of the application.

    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute module("eu.h2020.helios_social.core.messaging:messaging") with project(':messaging')
            substitute module("eu.h2020.helios_social.core.messaging_nodejslibp2p:messaging_nodejslibp2p") with project(":messaging_nodejslibp2p")
        }
    }

If local versions of the libraries are used those should be added to
the settings.gradle file of the application. The file is used to bind
the library to a filename path:

    include ':app'
    include ':messaging'
    project(':messaging').projectDir = new File("../messaging/lib")

## Internal structure of the library ##

The library consists of the following high-level submodules and interfaces:

* **Data classes** - Subdirectory *data* contains classes that can be used in
  message handling to store messages and conversations. HELIOS TestClient is
  using these classes but applications can also define their own data types.
  There is also JSON conversion class and storage helper class to load and
  store files into local filesystem.

* **Database** - Subdirectory *db* contains an interface to Android Room
  persistence library. It is used to store messages so that it is possible
  to resend messages for clients that have been offline for a while.
  Note that the application should call class *HeliosMessageStore* method
  to remove old entries so that the message cache does not grow all the
  time. Threshold time based removal routine has been implemented.

* **Synchronization** - Subdirectory *sync* contains classes that are used
  in message resending to synchronize state of the group discussion to
  clients that have been offline for a while or to resend failed one-to-one
  messages for clients that have been offline when the message has been
  sent. The library is sending periodic heartbeat messages to group
  communication topics. Those messages can be used to detect which users
  are currently online. *HeartbeatManager* class is handling sending of
  these heartbeat messages. *SyncManager* class is handling resending of
  one-to-one and group messages.

* **Reliable messaging** - Class *ReliableHeliosMessagingNodejsLibp2pImpl*
  can be used to send both publish-and-subscribe group messages and
  one-to-one direct messages. This class is using synchronization and
  database submodules to implement resending logic for clients that have
  been offline when the message was sent originally. This is now the
  preferred way to use HELIOS Messaging Library.

* **Messaging interfaces** - HELIOS Messaging library group messaging user
  is expected to implement *HeliosMessaging* interface and optionally also
  *HeliosConnect* interface. The client sending one-to-one messages should
  implement *HeliosDirectMessaging* interface. The application should
  implement a receiver class that implements *HeliosMessagingReceiver*
  interface. Note that the class *ReliableHeliosMessagingNodejsLibp2pImpl*
  already implements the sender side interfaces. 

* **Message protocol transport module** - HELIOS Messaging library module
  **messaging** must be used together with the messaging protocol transport
  module using js-libp2p library.

## API details ##

See javadocs in [javadocs.zip](https://raw.githubusercontent.com/helios-h2020/h.core-Messaging/master/doc/javadocs.zip).

### Interfaces ###

Helios **messaging** package contain few interface classes that define
interfaces that can be implemented in packages containing messaging
protocol implementations.

#### HeliosMessaging and HeliosConnect ####

Messaging protocol implementations are expected to implement
*HeliosMessaging* interface that is used to send publish-subscribe
group messages. The main methods of the interface are:

* `connect` - Connect to the network.

* `disconnect` - Disconnect from the network.

* `publish` - Publish a message to a specific topic.

* `subscribe` - Subscribe to receive messages from a specific topic.

* `unsubscribe` - Unsubscribe earlier topic subscription.

There is also related interface called HeliosConnect as some
implementations may want to check whether the connection has already
been established. This could be later merged to *HeliosMessaging*.

* `isConnected` - Return value true if the node is already connected

#### HeliosDirectMessaging ####

The clients that are sending one-to-one direct messages are expected
to implement these methods. Note that there are both synchronous and
asynchronous (implemented using Java *Future*) versions of the
*resolve* and *sendTo* method. The interface defines the following
methods:

* `resolve` - Map egoId string to *HeliosNetworkAddress*

* `resolveFuture` -  Asynchronous version of the previous method.

* `sendTo` - Send a message to a *HeliosNetworkAddress* using certain
   protocol identifier.

* `sendToFuture` - Asynchronous version of the previous method.

* `addReceiver`- Add receiver for the protocol.

* `removeReceiver` - Remove receiver for the protocol.

The class *HeliosDirectMessagingNodejsLibp2p* contains implementation
of this interface.

#### HeliosMessageListener ####

This is an interface that includes a callback function that is called
when a message is received from a connection. The interfacre has only
one function:

* `showMessage` - Process received publish/subscribe message.

#### HeliosMessagingReceiver ####

The interface defines two *receiveMessage* methods with
slightly different signatures. The client software is expected to
implement this interface in order to receive direct messages.

Class *ReliableHeliosMessagingNodejsLibp2pImpl* instantiates a private
class that implements this interface.

The interface contains two methods with the same name but different
signature:

* `receiveMessage` - Receive direct message with network address,
  protocol and data buffer as parameters.

* `receiveMessage` - Receive direct message with network address,
  protocol and file descriptor as parameters.

#### HeliosStorageHelper ####

This interface contains methods to download and upload files to
storage. Storage-specific classes are expected to implement the
methods of the interface.

### Container classes ###

HELIOS messaging classes specify data structures that could be used in
messaging applications.

#### HeliosMessage ####

The class encapsulates messages that will be sent to other nodes. The
current implementation is a string. Future version could add more
structured alternatives.

#### HeliosTopic #####

The class to encapsulate HELIOS topic.

#### HeliosTopicMessageContainer #####

The class encapsultes HeliosTopic and HeliosMessage into one container.

#### HeliosIdentityInfo ####

This contains the user nickname and a UUID for the user that should be
set. Note that the UUID should be generated once and thereafter be
reused. Application using this module should store and provide the
UUID for the use at this point.

### Testing ###

There are instrumented tests to test the use of Android Room persistence library interface
and few unit tests.

### Future work ###

* Message serialization (JSON or other) should be integrated into the Messaging module to have interoperability more easily. 
* Add suggestions here or create a new issue under this repository.

## Multiproject dependencies ##

HELIOS software components are organized into different repositories
so that these components can be developed separately avoiding many
conflicts in code integration. However, the modules may also depend
on each other. `Messaging` module depends on storage module. There
is a dependency in lib/build.gradle:

    implementation 'eu.h2020.helios_social.core.storage:storage:1.0.87'

### How to configure the dependencies ###

To manage project dependencies developed by the consortium, the
approach proposed is to use a private Maven repository with Nexus.

To avoid clone all dependencies projects in local, to compile the
"father" project. Otherwise, a developer should have all the projects
locally to be able to compile. Using Nexus, the dependencies are
located in a remote repository, available to compile, as described in
the next section.  Also to improve the automation for deploy,
versioning and distribution of the project.

### How to use the HELIOS Nexus ###

Similar to other dependencies available in Maven Central, Google or
others repositories. In this case we specify the Nexus repository
provided by Atos:

`https://builder.helios-social.eu/repository/helios-repository/`

This URL makes the project dependencies available.

To access, we simply need credentials, that we will define locally in
the variables `heliosUser` and `heliosPassword`.

The `build.gradle` of the project define the Nexus repository and the
credential variables in this way:

```
repositories {
        ...
        maven {
            url "https://builder.helios-social.eu/repository/helios-repository/"
            credentials {
                username = heliosUser
                password = heliosPassword
            }
        }
    }
```

And the variables of Nexus's credentials are stored locally at
`~/.gradle/gradle.properties`:

```
heliosUser=username
heliosPassword=password
```
To request Nexus username and password, contact Atos (jordi.hernandezv@atos.net).

### How to use the dependencies ###

To use the dependency in `build.gradle` of the "father" project, you
should specify the last version available in Nexus, related to the
last Jenkins's deploy.

## Android Studio project structure ##

This Android Studio Arctic Fox 2020.3.1 Patch 2 project contains the
following components:

* app - Messaging API test application

* doc - Additional documentation files

* lib - Messaging API implementation
