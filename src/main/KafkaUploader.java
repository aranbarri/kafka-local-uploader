package main;

import org.apache.commons.io.FileUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

public class KafkaUploader 
{
    String bootstrapServers;
    String topic;
    String watchDir;
    boolean modifySentFileNames;
    Properties config;
    
    private static final AtomicBoolean running = new AtomicBoolean(true);
    WatchService watchService;
    KafkaProducer<String, String> producer;
    
    public KafkaUploader(String propertiesPath) throws IOException
    {
        config = new Properties();
	config.load(new FileInputStream(propertiesPath));
       
	bootstrapServers = config.getProperty("bootstrap.servers");
        topic = config.getProperty("topic.name");
        watchDir = config.getProperty("watch.folder");
        modifySentFileNames = config.getProperty("modifySentFileNames").equalsIgnoreCase("true");
    }
   
    public static void main(String[] args) throws IOException, InterruptedException
    {    	
    	if (args.length!=1 || !args[0].trim().endsWith(".properties"))
	{
	   System.out.println("Arguments : [1] - Path to config.properties");
	   System.exit(0);
	}
    	String propertiesPath = args[0];
    	KafkaUploader uploader = new KafkaUploader(propertiesPath);
        uploader.start();
    }

    private void start() throws IOException, InterruptedException 
    {
    	producer = new KafkaProducer<>(config);
        watchService = FileSystems.getDefault().newWatchService();
        Paths.get(watchDir).register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

        System.out.println("Monitoring folder: " + watchDir);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> 
        {
            System.out.println("\nShutdown signal received. Cleaning up...");
            running.set(false);
            try {
                watchService.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            producer.close();
            System.out.println("Resources closed. Exiting.");
        }));

        while (running.get()) 
        {
            WatchKey key;
            try {
                key = watchService.poll(1, java.util.concurrent.TimeUnit.SECONDS); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) 
            {
            	stop();
                break;
            }

            if (key != null) 
            {
                for (WatchEvent<?> event : key.pollEvents()) 
                {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) 
                    {
                        String fileName = event.context().toString();
                        File file = new File(watchDir + "/" + fileName);

                        if (mustBeUploaded(file)) 
                        {
                            System.out.println("New file detected: " + fileName);
                            waitForFileCopy(file);
                            byte[] fileContent = FileUtils.readFileToByteArray(file);
                            String base64Image = Base64.getEncoder().encodeToString(fileContent);

                            ProducerRecord<String, String> record = new ProducerRecord<>(topic, fileName, base64Image);
                            producer.send(record, (metadata, exception) -> {
                                if (exception == null) {
                                    System.out.println("Sent " + fileName + " to Kafka topic " + metadata.topic());
                                } else {
                                    exception.printStackTrace();
                                }
                            });
                            
                            if (modifySentFileNames)
                            {
                                Path filePath = file.toPath();
                                Path uploadedPath = filePath.resolveSibling(filePath.getFileName() + ".uploaded");
                                Files.move(filePath, uploadedPath, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                }                
                key.reset();
            }
        }

	System.out.println("Exiting main thread.");
    }

    private static boolean mustBeUploaded(File file) 
    {
        String name = file.getName().toLowerCase();
        return !name.endsWith(".ignore") && !name.endsWith(".uploaded");
    }

    private static void waitForFileCopy(File file) throws InterruptedException 
    {
        long size = -1;
        while (true) 
	{
            long newSize = file.length();
            if (newSize == size) break;
            size = newSize;
            Thread.sleep(500);
        }
    }
	
    public void stop() 
    {
	try 
        {
	   if (watchService != null) {
	       watchService.close(); 
	   }
	} catch (IOException e) {
	        System.err.println("Error al cerrar WatchService: " + e.getMessage());
	}

	if (producer != null) 
        {
	   producer.flush();  
	   producer.close(); 
	 }
	 System.err.println("Closing KafkaLocalUploader");
    }
}
