
# KafkaUploader

A Java-based tool to **monitor a local directory** and **upload new files to an Apache Kafka topic**, encoding them in Base64.

## 📦 Features

- Watches a local folder for new file creations.
- Encodes file content as Base64 and sends to a Kafka topic.
- Automatically renames files after upload (optional).
- Ignores `.ignore` and `.uploaded` files.
---

## 🚀 Getting Started

### Prerequisites

- Java 8 or higher
- Apache Kafka cluster
- Kafka client libraries (configured via Maven or manually)
- [Apache Commons IO](https://commons.apache.org/proper/commons-io/) (for `FileUtils`)

### Compile

If you're using Maven, include:

```xml
<dependencies>
  <dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
    <version>3.6.0</version>
  </dependency>
  <dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
    <version>2.11.0</version>
  </dependency>
</dependencies>
```

Then compile:

```bash
mvn clean package
```

---

## ⚙️ Configuration

Create a properties file (e.g., `config.properties`) with the following keys:

```properties
bootstrap.servers=localhost:9092
topic.name=your-topic-name
watch.folder=/path/to/watch/folder
modifySentFileNames=true
```

- `bootstrap.servers`: Kafka broker(s) to connect to.
- `topic.name`: Kafka topic to publish messages to.
- `watch.folder`: Local folder to monitor for new files.
- `modifySentFileNames`: If `true`, renames uploaded files to `filename.uploaded`.

---

## 🏁 Usage

Run the application:

```bash
java -cp target/your-jar-with-dependencies.jar main.KafkaUploader config.properties
```

### Notes:
- Only new files created in the `watch.folder` will be processed.
- Files are encoded in Base64 before being sent to Kafka.
- Uploading waits until the file copy is complete (based on file size stability).

---

## 🧼 Shutdown & Cleanup

- App listens for shutdown signals (e.g., Ctrl+C).
- Ensures the Kafka producer and watcher service are gracefully closed.
- Files that are uploaded can be renamed (e.g., `myfile.txt` → `myfile.txt.uploaded`).

---

## 🚫 Ignored Files

The following files are skipped:

- Files ending with `.ignore`
- Files already uploaded (ending in `.uploaded`)

---

## 📄 License

This project is licensed under your preferred license (e.g., MIT, Apache 2.0).
