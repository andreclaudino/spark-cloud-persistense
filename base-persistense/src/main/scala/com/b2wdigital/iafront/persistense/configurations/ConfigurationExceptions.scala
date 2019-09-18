package com.b2wdigital.iafront.persistense.configurations

object ConfigurationExceptions {
  class IaFrontPersistenseException(message:String) extends Exception(message)

  class ConfigurationEnvNotFoundException(envName:String) extends IaFrontPersistenseException(s"Configuration environment variable '$envName' not found")
  class InvalidTableNameException(tableName:String) extends IaFrontPersistenseException(s"Invalid table name $tableName")
}
