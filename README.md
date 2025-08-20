# Kafka Chat

Простое Swing-приложение для обмена сообщениями через Kafka.

🔗 [Основная ветка проекта на GitHub](https://github.com/tatsianabrehova/maven-chat/tree/main)

---

## Установка и запуск Kafka-брокера на localhost

### С помощью Docker Compose

1. Убедитесь, что у вас установлен [Docker](https://www.docker.com/).
2. Перейдите в папку `deploy` и запустите Kafka:

```bash
cd deploy
docker-compose up -d
