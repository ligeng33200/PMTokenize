package com.pyaanalytics

import com.mongodb.hadoop.{MongoInputFormat, MongoOutputFormat}
import com.mongodb.hadoop.io.MongoUpdateWritable
import epic.preprocess
import org.apache.hadoop.conf.Configuration
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.bson.{BSONObject, BasicBSONObject}
import rapture.core._
import rapture.core.modes.returnTry
import rapture.json._
import rapture.json.jsonBackends.json4s._
import scala.util.{Success, Try}

object PMTokenize {

  def main(args: Array[String]) {

    val sc = new SparkContext("local", "Pubmed Tokenizer")

    val config = new Configuration()
    config.set("mongo.input.uri", "mongodb://10.250.1.31:27017/pubmedtest.pubmedtestcol")
    // output uri doesn't matter here since we're writing back to the same db.
    config.set("mongo.output.uri", "mongodb://10.250.1.31:27017/pubmedtest.pubmedtestcol")

    val mongoRDD = sc.newAPIHadoopRDD(
      config,
      classOf[MongoInputFormat],
      classOf[Object],
      classOf[BSONObject]
    )

    // mongoRDD contains (ObjectId, BSONObject) tuples
    val tokensRDD = mongoRDD.flatMap(arg => {
      val json = Json.parse(arg._2.toString) getOrElse Json("No Abstract")
      json
        .MedlineCitation
        .Article
        .Abstract
        .AbstractText
        .as[String] match {
          case Success(textChunk) => {
            val tokens = preprocess.preprocess(textChunk).toArray map (_.toArray)
            val query = new BasicBSONObject("_id", arg._1)
            val update = new BasicBSONObject("$set", (new BasicBSONObject("AbstractTokens", tokens)))
            val muw = new MongoUpdateWritable(query, update, false, true)
            Some(null, muw)
          }
          case _ => None
        }
    })

    // Generate a count to see how many abstracts we found
    val abstractCount = tokensRDD.count()

    // Perform the actual write to MongoDB
    tokensRDD.saveAsNewAPIHadoopFile(
      "file:///bogus",
      classOf[Any],
      classOf[Any],
      classOf[MongoOutputFormat[Any, Any]],
      config
    )

    println("Found and tokenized " + abstractCount + " abstracts.")
    sc.stop()
  }
}
