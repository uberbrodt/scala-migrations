/*
 * Copyright (c) 2010 Sony Pictures Imageworks Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the
 * distribution.  Neither the name of Sony Pictures Imageworks nor the
 * names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.imageworks.migration

trait DerbyAutoIncrementingColumnDefinitionMixin
    extends ColumnDefinition
    with ColumnSupportsAutoIncrement {
  override protected abstract def sql: String = {
    if (isAutoIncrement) super.sql + " GENERATED BY DEFAULT AS IDENTITY"
    else super.sql
  }
}

// Derby 10.2 and greater support an optional limit on the BLOB's
// size.
class DerbyBlobColumnDefinition
  extends DefaultBlobColumnDefinition
  with ColumnSupportsLimit

class DerbyBigintColumnDefinition
  extends DefaultBigintColumnDefinition
  with DerbyAutoIncrementingColumnDefinitionMixin

class DerbyIntegerColumnDefinition
  extends DefaultIntegerColumnDefinition
  with DerbyAutoIncrementingColumnDefinitionMixin

class DerbySmallintColumnDefinition
  extends DefaultSmallintColumnDefinition
  with DerbyAutoIncrementingColumnDefinitionMixin

// Derby does not support size specifiers for the TIMESTAMP data type.
class DerbyTimestampColumnDefinition
    extends ColumnDefinition
    with ColumnSupportsDefault {
  override protected def sql = "TIMESTAMP"
}

class DerbyVarbinaryColumnDefinition
    extends DefaultVarbinaryColumnDefinition {
  override protected def sql = {
    optionallyAddLimitToDataType("VARCHAR") + " FOR BIT DATA"
  }
}

class DerbyDatabaseAdapter(override val schemaNameOpt: Option[String])
    extends DatabaseAdapter(schemaNameOpt) {
  override val vendor = Derby

  override val quoteCharacter = '"'

  override val unquotedNameConverter = UppercaseUnquotedNameConverter

  override val userFactory = PlainUserFactory

  override val alterTableDropForeignKeyConstraintPhrase = "CONSTRAINT"

  override val addingForeignKeyConstraintCreatesIndex = true

  override val supportsCheckConstraints = true

  override def columnDefinitionFactory(columnType: SqlType,
                                       characterSetOpt: Option[CharacterSet]): ColumnDefinition = {
    characterSetOpt match {
      case None =>
      case Some(CharacterSet(Unicode, None)) =>
      case Some(charset @ CharacterSet(Unicode, Some(collation))) =>
        logger.warn("Ignoring collation '{}' in '{}' as Derby only " +
          "supports setting the collation when the database " +
          "is created.",
          Array[AnyRef](collation, charset): _*)
      case Some(charset @ CharacterSet(_, _)) =>
        logger.warn("Ignoring '{}' as Derby uses Unicode sequences to " +
          "represent character data types.",
          charset)
    }

    columnType match {
      case BigintType =>
        new DerbyBigintColumnDefinition
      case BlobType =>
        new DerbyBlobColumnDefinition
      case BooleanType => {
        val message = "Derby 10.6 and older do not support BOOLEAN as a " +
          "legal data type, you must choose a mapping yourself."
        throw new UnsupportedColumnTypeException(message)
      }
      case UuidType => {
        val message = "Derby does not support UUID as a legal data type"
        throw new UnsupportedColumnTypeException(message)
      }
      case CharType =>
        new DefaultCharColumnDefinition
      case DecimalType =>
        new DefaultDecimalColumnDefinition
      case IntegerType =>
        new DerbyIntegerColumnDefinition
      case SmallintType =>
        new DerbySmallintColumnDefinition
      case TimestampType =>
        new DerbyTimestampColumnDefinition
      case VarbinaryType =>
        new DerbyVarbinaryColumnDefinition
      case VarcharType =>
        new DefaultVarcharColumnDefinition
    }
  }

  override protected def alterColumnSql(schemaNameOpt: Option[String],
                                        columnDefinition: ColumnDefinition): String = {
    new java.lang.StringBuilder(512)
      .append("ALTER TABLE ")
      .append(quoteTableName(schemaNameOpt, columnDefinition.getTableName))
      .append(" ALTER ")
      .append(quoteColumnName(columnDefinition.getColumnName))
      .append(" SET DATA TYPE ")
      .append(columnDefinition.toSql)
      .toString
  }
}
