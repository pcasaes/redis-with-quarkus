# redis-with-quarkus project

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

This project serves to evaluate redis clients on Quarkus.

## Running the application in dev mode

First startup a redis cluster using docker

    ./start.sh
    
If you wish to remove it

    ./clean.sh    

You can run your application in dev mode that enables live coding using:
```
./mvnw quarkus:dev
If you want to learn more about building native executables, please consult https://quarkus.io/guides/building-native-image.
```

Use curl to test it out

    curl -X POST -i http://localhost:8080/lettuce\?key\=mykey\&value\=v1
    curl -X GET -i http://localhost:8080/lettuce\?key\=mykey
    
    curl -X POST -i http://localhost:8080/vertx\?key\=mykey\&value\=v2
    curl -X GET -i http://localhost:8080/vertx\?key\=mykey
    
    curl -X POST -i http://localhost:8080/mutiny-vertx\?key\=mykey\&value\=v2
    curl -X GET -i http://localhost:8080/mutiny-vertx\?key\=mykey
    
    