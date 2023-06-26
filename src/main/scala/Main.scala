import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.SaveMode
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql.Row
import org.apache.spark.sql.types.{StructField, StructType}
import org.apache.spark.sql.functions.{col, from_json}
import org.apache.spark.sql.{DataFrame, SQLContext, SparkSession}

object GalaxyTry {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession
      .builder
      //.enableHiveSupport()
      .appName("smdmitrieva")
      .getOrCreate()

    def GetUrlContentJson(url: String): (DataFrame, DataFrame) ={
      val result = scala.io.Source.fromURL(url)("UTF-8").mkString
      val jsonResponseOneLine = result.toString().stripLineEnd

      val jsonRdd = spark.sparkContext.parallelize(jsonResponseOneLine :: Nil)
      val jsonDf = spark.read.option("multiline", true).json(jsonRdd)

      implicit class DFHelpers(df: DataFrame) {
        def columns = {
          val dfColumns = df.columns.map(_.toLowerCase)
          df.schema.fields.flatMap { data =>
            data match {
              case column if column.dataType.isInstanceOf[StructType] => {
                column.dataType.asInstanceOf[StructType].fields.map { field =>
                  val columnName = column.name
                  val fieldName = field.name
                  col(s"${columnName}.${fieldName}").as(s"${columnName}_${fieldName}")
                }.toList
              }
              case column => List(col(s"${column.name}"))
            }
          }
        }

        def flatten: DataFrame = {
          val empty = df.schema.filter(_.dataType.isInstanceOf[StructType]).isEmpty
          empty match {
            case false =>
              df.select(columns: _*).flatten
            case _ => df
          }
        }
        def explodeColumns = {
          def columns(cdf: DataFrame):DataFrame = cdf.schema.fields.filter(_.dataType.typeName == "array") match {
            case c if !c.isEmpty => columns(c.foldLeft(cdf)((dfa,field) => {
              dfa.withColumn(field.name,explode_outer(col(s"${field.name}"))).flatten
            }))
            case _ => cdf
          }
          columns(df.flatten)
        }
      }

      val outputDF=jsonDf.explodeColumns
      //outputDF.printSchema()
      //outputDF.show()
      return (outputDF, jsonDf)
      }

    val methods = Array("req")//, "egrDetails", "analytics", "contacts", "buh", "licences", "finanValues", "bankAccounts", "bankGuarantees", "companyAffiliates/req", "companyAffiliates/egrDetails", "companyAffiliates/analytics", "fssp", "govPurchasesOfCustomer", "govPurchasesOfParticipant", "purchasesOfCustomer", "purchasesOfParticipant", "beneficialOwners", "fsa", "licensedActivities", "foundersHistory", "fnsBlockedBankAccounts", "inspections", "lessee", "petitionersOfArbitration", "companyBankruptcy", "pledger", "sites", "taxes", "trademarks")
    val responses:Array[(DataFrame, DataFrame)] = new Array[(DataFrame, DataFrame)](methods.length)
    val plainResponses:Array[DataFrame] = new Array[DataFrame](methods.length)
    var i = 0
    var plainResult:DataFrame = null
    for (method <- methods) {
      responses(i) = GetUrlContentJson("https://focus-api.kontur.ru/api3/"+method+"?key=хххх&inn=6663003127")
      if (i==0) {
        plainResult = responses(i)._2
      }
      else {
        plainResult = plainResult.join(responses(i)._2, Seq("inn","ogrn", "focusHref"))
      }
      i += 1
    }
    /*for (response <- responses) {
      response.show()
    }
    var response1 = GetUrlContentJson("https://focus-api.kontur.ru/api3/"+"contacts"+"?key=хххх&inn=6663003127")
    var response2 = GetUrlContentJson("https://focus-api.kontur.ru/api3/"+"buh"+"?key=хххх&inn=6663003127")
    response1._1.show()
    response1._2.show()
    response2._1.show()
    response2._2.show()
    //var result = response1._2.unionByName(response2._2)
    var result = response1._2.join(response2._2, response1._2("inn") === response2._2("inn"))*/
    plainResult.show()
    spark.sql("create database galaxy_test")
    plainResult.write.mode(SaveMode.Overwrite).saveAsTable("galaxy_test.req")
  }
}

object Transfer {
  def main(args: Array[String]): Unit = {

    var argss: Map[String, String] = Map(
      "user" -> "smdmitrieva",
      "url" -> "jdbc:postgresql://ingress-1.prod.dmp.vimpelcom.ru:5448/demo",
      "jaasLogin" -> "false",
      "driver" -> "org.postgresql.Driver",
      "dbtable" -> "bookings.seats",
      "outTableName" -> "school_de.seats_smdmitrieva",
      "dbtable2" -> "bookings.flights_v",
      "outTableName2" -> "school_de.flights_v_smdmitrieva",
      "password" -> "")

    for (i <- args) {
      val argi = i.split("=")
      argss += (argi(0) -> argi(1))
    }

    val spark = SparkSession
      .builder
      .enableHiveSupport()
      .appName("smdmitrieva")
      .getOrCreate()

    val jdbcDF = spark.read
      .format("jdbc")
      .option("driver", argss("driver"))
      .option("url", argss("url"))
      .option("dbtable", argss("dbtable"))
      .option("jaasLogin", argss("jaasLogin"))
      .option("user", argss("user"))
      .option("password", argss("password"))
      .load()

    val jdbcDF2 = spark.read
      .format("jdbc")
      .option("driver", argss("driver"))
      .option("url", argss("url"))
      .option("dbtable", argss("dbtable2"))
      .option("jaasLogin", argss("jaasLogin"))
      .option("user", argss("user"))
      .option("password", argss("password"))
      .load()

    jdbcDF.write.mode(SaveMode.Overwrite).saveAsTable(argss("outTableName"))
    jdbcDF2.withColumn("date_actual_departure", to_date(col("actual_departure"))).write.mode(SaveMode.Overwrite).partitionBy("date_actual_departure").saveAsTable(argss("outTableName2"))
    spark.stop()
  }
}
object Galaxy {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession
      .builder
      .enableHiveSupport()
      .appName("smdmitrieva")
      .getOrCreate()

    val server_name = "jdbc:sqlserver://ms-b2ban001.bee.vimpelcom.ru:1433"
    val database_name = "spark"
    val url = server_name + ";" + "databaseName=" + database_name + ";" /*+ "integratedSecurity=true" + ";" + "authenticationScheme=JavaKerberos;"*/
    val table_name = "dbo.DP_all_easy_tags"
    val username = "Spark_data"
    val password ="!ti9PLd7[3"
    val jdbc_df = (spark.read
      .format("jdbc")
      .option("driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver")
      .option("url", url)
      .option("dbtable", table_name)
      .option("user", username)
      .option("password", password)
      .load())

    val lookup = Map("inn" -> "inn",
      "Юр_адрес_компании" -> "uridicheskii_adress",
      "Количество_адресов" -> "kolvo_adressov",
      "БИК" -> "bik",
      "Уставной_капитал" -> "ustavnoy_capital",
      "Кол_адресов_ликв_компании" -> "kolvo_adressov_likv_kompanii",
      "Адрес_без_номера_ликв_компании" -> "adress_bez_nomera_likv_kompanii",
      "Размер_компании" -> "razmer_kompanii",
      "Тип_компании" -> "tip_kompanii",
      "Сводный_показатель" -> "svodniy_pokazatel",
      "Кредитный_лимит" -> "kreditniy_limit",
      "Дата_первой_регистрации" -> "data_pervoy_registracii",
      "ЕГРПО_включен" -> "egrpo_vkluchen",
      "Внесение_в_ЕГРЮЛ" -> "vnesenie_v_ugrl",
      "Email" -> "email",
      "Активное_исполнительное_производство" -> "aktivnoe_ispolnitelnoe_proizvodstvo",
      "Выполненное_исполнительное_производство" -> "vipolnennoe_ispolnitelnoe_proizvodstvo",
      "Индекс_фин_риска" -> "indeks_finansovogo_riska",
      "Код_ФЦСМ" -> "kod_fcsm",
      "Дата_гос_регистрации" -> "data_gos_registracii",
      "Орган_гос_регистрации" -> "organ_gos_registracii",
      "Адрес_гос_регистрации" -> "adress_gos_registracii",
      "Дата_текущей_рег_в_фед_налоговой" -> "data_tekushei_registracii_v_fed_nalogovoi",
      "Орган_текущей_рег_в_фед_налоговой" -> "organ_tekushei_registracii_v_fed_nalogovoi",
      "Адрес_текущей_рег_в_фед_налоговой" -> "adres_tekushei_registracii_v_fed_nalogovoi",
      "Дата_оплаты_рег_в_Фед_налоговой" -> "data_oplati_registracii_v_fed_nalogovoi",
      "Регистрирующий_орган_оплаченой_рег_в_Фед_налоговой" -> "reg_organ_opl_reg_v_fed_nalog",
      "Адрес_оплаченной_рег_в_Фед_налоговой" -> "adress_oplachennoi_registracii_v_nalogovoi",
      "Код_оплаченной_рег_в_Фед_налоговой" -> "kod_oplachennoi_registracii_v_nalogovoi",
      "Полное_название_организации" -> "polnoe_nazvanie_organizacii",
      "GUID" -> "guid",
      "Индекс_должной_осмотрительности" -> "indeks_dolzhnoi_osmotritelnosti",
      "Статус_активности" -> "status_aktivnosti",
      "КПП" -> "kpp",
      "Руководитель" -> "rukovoditel",
      "Название_организации" -> "nazvanie_organizacii",
      "ОГРН" -> "ogrn",
      "ОКАТО_код" -> "okato_kod",
      "ОКАТО_регион" -> "okato_region",
      "ОКАТО_код_региона" -> "okato_kod_region",
      "ОКФС_код" -> "okfs_kod",
      "ОКФС_форма_собственности" -> "oks_forma_sobstvennosti",
      "ОКОГУ_код" -> "okogu_kod",
      "ОКОГУ_название" -> "okogu_nazvanie",
      "ОКОПФ_код" -> "okopf_kod",
      "ОКОПФ_новый_код" -> "okopf_novii_kod",
      "ОКОПФ_название" -> "okopf_nazvanie",
      "ОКПО" -> "okpo",
      "OKTMO" -> "oktmo",
      "Код_главного_ОКВЭДа" -> "kod_glavn_okveda",
      "Название_главного_ОКВЭДа" -> "imya_glavn_okveda",
      "Активные_обязательства_по_взносам" -> "aktivnie_obyazatelstva_po_vznosam",
      "Закрытые_обязательства_по_взносам" -> "zakritie_obyazatelstva_po_vznosam",
      "Выручка_за_2015" -> "viruchka_2015",
      "Выручка_за_2016" -> "viruchka_2016",
      "Выручка_за_2017" -> "viruchka_2017",
      "Выручка_за_2018" -> "viruchka_2018",
      "Выручка_за_2019" -> "viruchka_2019",
      "Выручка_за_2020" -> "viruchka_2020",
      "Выручка_за_2021" -> "viruchka_2021",
      "Адрес_налоговой" -> "adress_nalogovoi",
      "Рег_номер" -> "reg_nomer",
      "Rep_Inn" -> "rep_inn",
      "Численность_сотрудников" -> "chislennost_sotrudnikov",
      "Спарк_ID" -> "spark_id",
      "Тип_статуса" -> "tip_statusa",
      "Группа_статуса" -> "group_statusa",
      "Дата_статуса" -> "data_statusa",
      "Налоги_за_2017" -> "nalogi_za_2017",
      "Налоги_за_2018" -> "nalogi_za_2018",
      "Налоги_за_2019" -> "nalogi_za_2019",
      "Налоги_за_2020" -> "nalogi_za_2020",
      "Налоги_за_2021" -> "nalogi_za_2021",
      "Диапазон_рабочих" -> "diapazon_rabochih",
      "Сайт" -> "site",
      "Телефон" -> "telephone",
      "update_date" -> "update_date")

    val jdbc_df2 = jdbc_df.select(jdbc_df.columns.map(c => col(c).as(lookup.getOrElse(c, c))): _*)

    (jdbc_df2
      .write
      .mode(SaveMode.Overwrite)
      .format("orc")
      .option("orc.compress","SNAPPY")
      .option("orc.create.index","true")
      .option("orc.encoding.strategy","SPEED")
      .option("auto.purge","true")
      .option("org.apache.spark.sql.SaveMode", "overwrite")
      .saveAsTable("galaxy_stg.s_p_a_r_k_smdmitrieva")
      )
  }
}

object Evaluate {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession
      .builder
      .enableHiveSupport()
      .appName("smdmitrieva")
      .getOrCreate()
    spark.conf.set("spark.sql.session.timeZone", "UTC")
    import spark.implicits._

    val jdbcDFbookings = spark.sqlContext.table("school_de.bookings_bookings")
    val jdbcDFtickets = spark.sqlContext.table("school_de.bookings_tickets")
    val jdbcDFtickets_flights = spark.sqlContext.table("school_de.bookings_ticket_flights")
    val jdbcDFflights = spark.sqlContext.table("school_de.bookings_flights")
    val jdbcDFairports = spark.sqlContext.table("school_de.bookings_airports")
    val jdbcDFaircrafts = spark.sqlContext.table("school_de.bookings_aircrafts")

    val schema = StructType(
      StructField("id", IntegerType, false) ::
        StructField("response", StringType, false)  :: Nil)
    var result = spark.createDataFrame(spark.sparkContext.emptyRDD[Row], schema)

    //1
    var sqlDF = jdbcDFtickets.groupBy("book_ref").agg(count("*").alias("pass_num")).agg(max("pass_num").alias("response"))
    result = result.union(sqlDF.withColumn("id", lit(1)).select("id","response"))

    //2
    var sqlDF2 = jdbcDFtickets.groupBy("book_ref").agg(count("*").alias("pass_num")).filter('pass_num > jdbcDFtickets.count().toFloat/jdbcDFbookings.count().toFloat)
    result = result.union(Seq((2,sqlDF2.count())).toDF("id","response"))

    //3
    var sqlDF3 = jdbcDFtickets.groupBy("book_ref")
      .agg(count("*").alias("pass_num"), sort_array(collect_list("passenger_id")).alias("passengers"))
      .filter('pass_num === result.filter('id === 1).select("response").as[String].collect()(0))
      .groupBy("passengers")
      .agg(count("*").alias("pass_books"))
      .filter('pass_books > 1)
    result = result.union(Seq((3,sqlDF3.count())).toDF("id","response"))

    //4
    var sqlDF4 = jdbcDFtickets.groupBy("book_ref")
      .agg(count("*").alias("pass_num"), sort_array(collect_list("passenger_id")).alias("passengers"))
      .filter('pass_num === 3)

    var sqlDF42 = jdbcDFtickets
      .join(sqlDF4, sqlDF4("book_ref") === jdbcDFtickets("book_ref"))
      .select(concat_ws("|", sqlDF4("book_ref"), 'passenger_id, 'passenger_name, 'contact_data).alias("response"))
      .withColumn("id", lit(4))
      .select("id", "response")
      .orderBy("response")
    result = result.union(sqlDF42)

    //5
    var sqlDF5 = jdbcDFtickets
      .join(jdbcDFtickets_flights, jdbcDFtickets_flights("ticket_no") === jdbcDFtickets("ticket_no"))
      .groupBy("book_ref")
      .agg(count("*").alias("response"))
      .orderBy('response desc)
      .limit(1)
    result = result.union(sqlDF5.withColumn("id", lit(5)).select("id","response"))

    //6
    var sqlDF6 = jdbcDFtickets
      .join(jdbcDFtickets_flights, jdbcDFtickets_flights("ticket_no") === jdbcDFtickets("ticket_no"))
      .groupBy('book_ref, 'passenger_id)
      .agg(count("*").alias("response"))
      .orderBy('response desc)
      .limit(1)
    result = result.union(sqlDF6.withColumn("id", lit(6)).select("id","response"))

    //7
    var sqlDF7 = jdbcDFtickets
      .join(jdbcDFtickets_flights, jdbcDFtickets_flights("ticket_no") === jdbcDFtickets("ticket_no"))
      .groupBy('passenger_id)
      .agg(count("*").alias("response"))
      .orderBy('response desc)
      .limit(1)
    result = result.union(sqlDF7.withColumn("id", lit(7)).select("id","response"))

    //8
    var min_pas_sum = jdbcDFtickets
      .join(jdbcDFtickets_flights, jdbcDFtickets_flights("ticket_no") === jdbcDFtickets("ticket_no"))
      .join(jdbcDFflights, jdbcDFflights("flight_id") === jdbcDFtickets_flights("flight_id"))
      .filter(not('status === "Cancelled"))
      .groupBy('passenger_id)
      .agg(sum('amount).alias("passenger_amount"))
      .orderBy('passenger_amount)
      .limit(1)
      .select('passenger_amount)
      .collect()

    var sqlDF8 = jdbcDFtickets
      .join(jdbcDFtickets_flights, jdbcDFtickets_flights("ticket_no") === jdbcDFtickets("ticket_no"))
      .join(jdbcDFflights, jdbcDFflights("flight_id") === jdbcDFtickets_flights("flight_id"))
      .filter(not('status === "Cancelled"))
      .groupBy('passenger_id, 'passenger_name, 'contact_data)
      .agg(sum('amount).alias("passenger_amount"))
      .filter('passenger_amount === (min_pas_sum(0))(0))
      .select(concat_ws("|",'passenger_id, 'passenger_name, 'contact_data, 'passenger_amount)
        .alias("response"))
      .withColumn("id", lit(8))
      .select("id", "response")
      .orderBy("response")

    result = result.union(sqlDF8)

    //9
    var max_flight_time = jdbcDFtickets
      .join(jdbcDFtickets_flights, jdbcDFtickets_flights("ticket_no") === jdbcDFtickets("ticket_no"))
      .join(jdbcDFflights, jdbcDFflights("flight_id") === jdbcDFtickets_flights("flight_id"))
      .filter(('status === "Arrived"))
      .groupBy('passenger_id)
      .agg(sum(('actual_arrival.cast(LongType)-'actual_departure.cast(LongType))).alias("passenger_flight_time"))
      .orderBy('passenger_flight_time desc)
      .limit(1)
      .select('passenger_flight_time)
      .collect()

    var h = (max_flight_time(0))(0).toString.toInt / 3600
    var m = ((max_flight_time(0))(0).toString.toInt / 60) % 60
    var s = (max_flight_time(0))(0).toString.toInt % 60
    var res_time = "%02d:%02d:%02d".format(h, m, s)

    var sqlDF9 = jdbcDFtickets
      .join(jdbcDFtickets_flights, jdbcDFtickets_flights("ticket_no") === jdbcDFtickets("ticket_no"))
      .join(jdbcDFflights, jdbcDFflights("flight_id") === jdbcDFtickets_flights("flight_id"))
      .filter(('status === "Arrived"))
      .groupBy('passenger_id, 'passenger_name, 'contact_data)
      .agg(sum('actual_arrival.cast(LongType)-'actual_departure.cast(LongType)).alias("passenger_flight_time"))
      .filter('passenger_flight_time === (max_flight_time(0))(0))
      .select(concat_ws("|",'passenger_id, 'passenger_name, 'contact_data, lit(res_time))
        .alias("response"))
      .withColumn("id", lit(9))
      .select("id", "response")
      .orderBy("response")

    result = result.union(sqlDF9)

    //10
    var sqlDF10 = jdbcDFairports
      .groupBy('city.alias("response"))
      .count()
      .filter($"count" > 1)
      .orderBy($"count" desc, 'response)
      .withColumn("id", lit(10))
      .select("id", "response")

    result = result.union(sqlDF10)

    //11
    var sqlDF11_1 = jdbcDFflights
      .join(jdbcDFairports.as("a"), col("a.airport_code") === jdbcDFflights("departure_airport"))
      .join(jdbcDFairports.as("aa"), col("aa.airport_code") === jdbcDFflights("arrival_airport"))
      .select(col("a.city").alias("city1"), col("aa.city").alias("city2"))
      .distinct()
    var sqlDF11_2 = jdbcDFflights
      .join(jdbcDFairports.as("a"), col("a.airport_code") === jdbcDFflights("departure_airport"))
      .join(jdbcDFairports.as("aa"), col("aa.airport_code") === jdbcDFflights("arrival_airport"))
      .select(col("aa.city").alias("city1"), col("a.city").alias("city2"))
      .distinct()
    var sqlDF11 = sqlDF11_1
      .union(sqlDF11_2)
      .distinct()
      .groupBy('city1)
      .agg(count("*").alias("cities_num"))

    var min_airports = sqlDF11
      .orderBy('cities_num)
      .limit(1)
      .select('cities_num)
      .collect()(0)(0)

    var sqlDF11_3 = sqlDF11
      .filter('cities_num === min_airports)
      .orderBy('city1)
      .withColumn("id", lit(11))
      .select('id, 'city1.alias("response"))

    result = result.union(sqlDF11_3)

    //12
    var sqlDF12 =
      jdbcDFairports.as("a")
        .join(jdbcDFairports.as("aa"))
        .select(col("aa.city").alias("city1"), col("a.city").alias("city2"))
        .except(
          sqlDF11_1
            .union(sqlDF11_2)
            .distinct()
        )
        .filter('city1 < 'city2)
        .orderBy('city1, 'city2)
        .withColumn("id", lit(12))
        .select('id, concat_ws("|",'city1, 'city2).alias("response"))

    result = result.union(sqlDF12)

    //13
    var sqlDF13 =
      jdbcDFairports.as("a")
        .join(jdbcDFairports.as("aa"))
        .select(col("aa.city").alias("city1"), col("a.city").alias("city2"))
        .except(
          sqlDF11_1
            .union(sqlDF11_2)
            .distinct()
        )
        .filter('city1 === "Москва")
        .filter('city1 =!= 'city2)
        .orderBy('city2)
        .withColumn("id", lit(13))
        .select('id, ('city2).alias("response"))

    result = result.union(sqlDF13)

    //14
    var sqlDF14 = jdbcDFaircrafts
      .join(jdbcDFflights, jdbcDFaircrafts("aircraft_code") === jdbcDFflights("aircraft_code"))
      .filter('status === "Arrived")
      .groupBy('model)
      .agg(count("*").alias("count"))
      .orderBy('count desc)
      .limit(1)
      .withColumn("id", lit(14))
      .select('id, ('model).alias("response"))

    result = result.union(sqlDF14)

    //15
    var sqlDF15 = jdbcDFtickets_flights
      .join(jdbcDFflights.filter('status === "Arrived"), jdbcDFtickets_flights("flight_id") === jdbcDFflights("flight_id"))
      .join(jdbcDFaircrafts, jdbcDFaircrafts("aircraft_code") === jdbcDFflights("aircraft_code"))
      .groupBy(jdbcDFaircrafts("model"))
      .agg(count("*").alias("count"))
      .orderBy('count desc)
      .limit(1)
      .withColumn("id", lit(15))
      .select('id, ('model).alias("response"))

    result = result.union(sqlDF15)

    var sqlDF16 = jdbcDFflights
      .filter(('status === "Arrived"))
      .select(
        (abs('actual_arrival.cast(LongType)-'actual_departure.cast(LongType)) -
          ('scheduled_arrival.cast(LongType)-'scheduled_departure.cast(LongType))
          ).alias("time"))
      .agg(((sum('time)/60).cast(IntegerType)).alias("response"))
      .withColumn("id", lit(16))
      .select("id", "response")
    result = result.union(sqlDF16)

    //17
    var sqlDF17 = jdbcDFflights
      .filter((('actual_departure >= "2016-09-13") and ('actual_departure < "2016-09-14")))
      .filter(('status === "Arrived"))
      .join(jdbcDFairports.filter('city === "Санкт-Петербург").as("a"), col("a.airport_code") === jdbcDFflights("departure_airport"))
      .join(jdbcDFairports.as("aa"), col("aa.airport_code") === 'arrival_airport)
      .select(col("aa.city").alias("response"))
      .distinct()
      .orderBy('response)
      .withColumn("id", lit(17))
      .select("id", "response")
    result = result.union(sqlDF17)

    //18
    var sqlDF18 = jdbcDFflights
      .filter(('status === "Arrived"))
      .join(jdbcDFtickets_flights, jdbcDFtickets_flights("flight_id") === jdbcDFflights("flight_id") )
      .groupBy(jdbcDFflights("flight_id"))
      .agg((sum('amount)).alias("response_s"))
      .orderBy('response_s desc)
      .limit(1)
      .withColumn("id", lit(18))
      .select('id, 'flight_id.alias("response"))
    result = result.union(sqlDF18)

    //19
    var sqlDF19 = jdbcDFflights
      .filter(!('status === "Cancelled") and ('actual_departure.isNotNull))
      .groupBy(to_date('actual_departure).alias("response"))
      .agg(count("*").alias("num_flights"))
      .orderBy('num_flights)
      .limit(1)
      .withColumn("id", lit(19))
      .select('id, 'response)
    result = result.union(sqlDF19)

    //20
    var sqlDF20 = jdbcDFflights
      .filter((('actual_departure >= "2016-09-01") and ('actual_departure < "2016-10-01")))
      .filter(!('status === "Cancelled"))
      .join(jdbcDFairports.filter('city === "Москва").as("a"), col("a.airport_code") === jdbcDFflights("departure_airport"))
    result = result.union(Seq((20,(sqlDF20.count()).toString.toInt/30)).toDF("id","response"))

    //21
    var sqlDF21 = jdbcDFflights
      .filter(('status === "Arrived"))
      .join(jdbcDFairports, 'airport_code === 'departure_airport)
      .groupBy('city)
      .agg(((sum('actual_arrival.cast(LongType)-'actual_departure.cast(LongType)))/(count("*"))).alias("sum_a"))
      .filter('sum_a > 3*60*60)
      .orderBy('sum_a desc)
      .limit(5)
      .orderBy('city)
      .withColumn("id", lit(21))
      .select('id, ('city).alias("response"))
    result = result.union(sqlDF21)

    result.write.mode(SaveMode.Overwrite).saveAsTable("school_de.results_smdmitrieva")

    spark.stop()
  }
}
