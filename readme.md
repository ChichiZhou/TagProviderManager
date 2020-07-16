# Managed Tag Provider

## 1 Create Tags 
The name of the tag provider is ``Example``


## 2 Publish Tags to AWS

Need to restart Ignition after install this module. Else there will be no value for the tags.

If set the period as 5 minutes, the graph is like following:
The value of 20:05 is the average value from 20:05 to 20:10. (Because the period is 5 mins)
From the AWS DOC:
When you retrieve statistics, you can specify a period, start time, and end time. 
These parameters determine the overall length of time associated with the statistics.
The default values for the start time and end time get you the last hour's worth of statistics. 
(https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/cloudwatch_concepts.html#Statistic)

![image](https://github.com/ChichiZhou/TagProviderManager/blob/newbranch/TEST1.png)


The value published to the AWS CloudWatch Metric is 10 which is the number of tags
