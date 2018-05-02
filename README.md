# plant-monitor

A simple web application that can monitor and watch the database for changes and stream events. In this application, we monitor the PowerPlant 
table and stream updates to a HTTP service endpoint.

TODO:

1. Add content to README
2. This application should do the following:
   2.1 When started, read all PowerPlant's from the PowerPlant table based on shard region
   2.2 Should read all PowerPlant's after the last read time
   2.3 For each read, send a message to the cluster backend which will then start a new Actor instance for that PowerPlant
   2.4 Additionally expose API#s to CRUD PowerPlant's 
3. We need to send specific messages depending on what happened to the PowerPlant in the database. For example., a PowerPlant
   could be newly added, another PowerPlant could be updated, while another could be deleted. We need to take into consideration
   all these cases
4. How To Implement This
   4.1 We will use Apache Kafka for decoupling, but this would mean that the cluster backend could not be load balanced
   4.2 Without Kafka, we could just have an Actor instance that reads for all PowerPlant's that was updated between the last 
       read time and the current time. We have to context become the update time. This has the drawback as there is
       no possibility to now if the PowerPlant was created new or updated. Of course we could have this information persisted
       in the database and use this as our anchor to trigger create, update or delete events. But what happens when dealing with 
       multiple instances against the same database? Remember that this application will run in a K8s cluster with a replication
       factor of 3. If this is replicated thrice, this would mean that all the three instances would be trying to do the same
       thing. We need to avoid this somehow! One solution is to leverage Akka Cluster and have just one Actor that is replicable
       across all th available instances such that one and only one instance exists.    
      
