/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.connect.planner

import org.apache.spark.SparkThrowableHelper
import org.apache.spark.connect.proto
import org.apache.spark.sql.catalyst.plans.PlanTest
import org.apache.spark.sql.connect.common.DataTypeProtoConverter
import org.apache.spark.sql.connect.common.InvalidPlanInput
import org.apache.spark.sql.connect.planner.SparkConnectPlanTest
import org.apache.spark.sql.types.{ArrayType, IntegerType}

class InvalidInputErrorsSuite extends PlanTest with SparkConnectPlanTest {

  val testCases = Seq(
    TestCase(
      name = "Invalid schema data type",
      expectedErrorCondition = "INVALID_SCHEMA_DATA_TYPE",
      expectedParameters = Map("dataType" -> "\"ARRAY<INT>\""),
      invalidInput = {
        val parse = proto.Parse
          .newBuilder()
          .setInput(readRel)
          .setSchema(DataTypeProtoConverter.toConnectProtoType(ArrayType(IntegerType)))
          .setFormat(proto.Parse.ParseFormat.PARSE_FORMAT_CSV)
          .build()

        proto.Relation.newBuilder().setParse(parse).build()
      }),
    TestCase(
      name = "Invalid schema non-struct type",
      expectedErrorCondition = "INVALID_SCHEMA.NON_STRUCT_TYPE",
      expectedParameters = Map(
        "inputSchema" -> """"{"type":"array","elementType":"integer","containsNull":false}"""",
        "dataType" -> "\"ARRAY<INT>\""),
      invalidInput = {
        val invalidSchema = """{"type":"array","elementType":"integer","containsNull":false}"""

        val dataSource = proto.Read.DataSource
          .newBuilder()
          .setFormat("csv")
          .setSchema(invalidSchema)
          .build()

        val read = proto.Read
          .newBuilder()
          .setDataSource(dataSource)
          .build()

        proto.Relation.newBuilder().setRead(read).build()
      }))

  // Run all test cases
  testCases.foreach { testCase =>
    test(s"${testCase.name}") {
      val exception = intercept[InvalidPlanInput] {
        transform(testCase.invalidInput)
      }
      val expectedMessage = SparkThrowableHelper.getMessage(
        testCase.expectedErrorCondition,
        testCase.expectedParameters)
      assert(exception.getMessage == expectedMessage)
    }
  }

  // Helper case class to define test cases
  case class TestCase(
      name: String,
      expectedErrorCondition: String,
      expectedParameters: Map[String, String],
      invalidInput: proto.Relation)
}
