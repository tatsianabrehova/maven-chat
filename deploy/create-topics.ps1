docker exec -it maven-chat-kafka-1 kafka-topics --create --topic chat-rooms --bootstrap-server host.docker.internal:9092 --partitions 1 --replication-factor 1
docker exec -it maven-chat-kafka-1 kafka-topics --create --topic private-messages --bootstrap-server host.docker.internal:9092 --partitions 1 --replication-factor 1
