package de.unihamburg.informatik.nosqlmark.util

import better.files._
import better.files.Dsl.SymbolicOperations
import de.unihamburg.informatik.nosqlmark.api.{CoreCounts, CoreJob, Job, StalenessJob}
import org.scalatest.{MustMatchers, WordSpecLike}


/**
  * Created by Steffen Friedrich on 15.02.2016.
  */
class JobSpec extends WordSpecLike with MustMatchers{

  "job update" must {
    "contain the updated values" in {
      val job = CoreJob()
      val update = """
      {
        "jobID" : "Test-001",
        "batchname" : "20160517_mongo_autoscale",
        "dbname" : "MongoDbClient",
        "dbproperties" : {
          "mongodb.url" : "mongodb://localhost:27017/ycsb?w=majority&readPreference=secondary"
        }
      }"""
      val newJob = Job.update(job, update)

      assert(newJob.jobID == "Test-001")
      assert(newJob.batchname == "20160517_mongo_autoscale")
      assert(newJob.dbname == "MongoDbClient")
      assert(newJob.dbproperties.getOrElse("mongodb.url","") == "mongodb://localhost:27017/ycsb?w=majority&readPreference=secondary")

    }
  }

  "YCSBWorkloadParser" must {
    "construct a valid Job" in {
      val myargs = Array("--workloadfile", "workloads/ycsb/workloade", "--dbname","CassandraCQLClient2", "--scanproportion",
        "0.9", "--readproportion", "0.05", "-Dhosts=10.10.100.16,10.10.100.19,10.10.100.20")
      val job = YCSBWorkloadParser.parse(myargs)

      assert(job.dbname == "CassandraCQLClient2")
      assert(job.dbproperties.get("hosts").get == "10.10.100.16,10.10.100.19,10.10.100.20")
      assert(job.proportions.scanproportion == 0.9)
      assert(job.proportions.readproportion == 0.05)
      assert(job.proportions.insertproportion == 0.05)
      assert(job.proportions.updateproportion == 0.0)
      assert(job.proportions.readmodifywriteproportion == 0.0)


      val file = File.newTemporaryFile("test", "json")
      Job.export(job,file)

      val jobFromJson = Job.loadJob(file)
      assert(jobFromJson.dbname == "CassandraCQLClient2")
      assert(jobFromJson.dbproperties.get("hosts").get == "10.10.100.16,10.10.100.19,10.10.100.20")
      assert(jobFromJson.proportions.scanproportion == 0.9)
      assert(jobFromJson.proportions.readproportion == 0.05)
      assert(jobFromJson.proportions.insertproportion == 0.05)
      assert(jobFromJson.proportions.updateproportion == 0.0)
      assert(jobFromJson.proportions.readmodifywriteproportion == 0.0)
    }
  }

  "Job.load()" must {
    " parse the json file correctly" in {
       val job = Job.loadJob("workloads/example_transactional.json")
       assert(job.isInstanceOf[CoreJob])
    }
  }

  "updated workload json to StalenessWorkload)" must {
    " end in an instance of StalenesJob" in {
      val job = Job.loadAbstractJob("workloads/example_transactional.json")
      val update = """
       {
        "workload" : "StalenessWorkload",
        "proportions" : {
          "stalenessmeasurementfraction" : 0.0
        }
      }"""
      val newJob = Job.update(job, update)
      assert(newJob.isInstanceOf[StalenessJob])
    }
  }


  "insert start for node 3 and transactional phase with CoreJob" must {
    "be correct" in {
      val job = CoreJob.load("workloads/example_transactional.json").copy(nodes = 3)

      // recordcount = 200 000, operationcount 40000
      // transactional phase must insert at 200 000 (node 1) +
      // 13334  (node 2) + 13333 (node 3)
      val workForNodes =  job.workForNodes
      assert(226667 == workForNodes.head.counts.insertstart)
    }
  }

  "insert start for node 3 and load phase with CoreJob" must {
    "be correct" in {
      val job = CoreJob.load("workloads/example_load.json").copy(nodes = 3)
      import de.unihamburg.informatik.nosqlmark.api.CoreJobFormats._

      val workForNodes =  job.workForNodes
      assert(13334 == workForNodes.head.counts.insertstart)
    }
  }


}
