TODO:

1. Add content to README
2. This application should do the following:
   2.1 When started, read all PowerPlant's from the PowerPlant table based on shard region
   2.2 Should read all PowerPlant's after the last read time
   2.3 For each read, send a message to the cluster backend which will then start a new Actor instance for that PowerPlant
   2.4 Additionally expose API#s to CRUD PowerPlant's 
