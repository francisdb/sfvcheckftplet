This ftplet enables sfv checking for [Apache Ftpserver](http://mina.apache.org/ftpserver)<br />
sfv files are commonly used to ensure the correct retrieval or storage of data.

## Features ##
  * detecting sfv files
  * creating -missing files
  * caching crc info for performance (using ehcache)
  * progress indication files
  * SITE RESCAN command to force rescan

## Installing on ftpserver ##

Check out the project repository

```
mvn clean install
```

copy target/sfvcheckftplet-1.0-SNAPSHOT.jar to <ftpserver root>/commom/lib

As sfvcheckftplet has an ehcache dependecy you will also need that file in the lib folder. You can copy it from
~/.m2/repository/net/sf/ehcache/ehcache/1.6.2/ehcache-1.6.2.jar

edit your ftpserver configuration

```
        ...
        </listeners>
        <!--
                Use this section to define your Ftplets, they are configured like
                regular Spring beans
        -->
        <ftplets>
                <ftplet name="sfvcheckftplet">
                        <beans:bean class="com.google.code.sfvcheckftplet.SfvCheckFtpLet">
                                <!--<beans:property name="foo" value="123" />-->
                        </beans:bean>
                </ftplet>
        </ftplets>
        ...
```

Start the server and the ftplet should be active.

```
bin/ftpd.sh res/conf/your-config.xml
```

## Test server ##
Check out the code and run this command to start a test server:

```
mvn clean install exec:java
```

now log in with and ftp client on:
  * host: localhost
  * port: 2221
  * user: test
  * pass: test

### Executable jar ###

To create an executable jar for testing use this maven command:

```
mvn clean install -Pexecutablejar
```

the target folder should now contain sfvcheckftplet-xxx-jar-with-dependencies.jar that you can start with

```
java -jar sfvcheckftplet-xxx-jar-with-dependencies.jar /folder/to/share
```

### Contact ###

Create issues for requesting new features or bug fixes