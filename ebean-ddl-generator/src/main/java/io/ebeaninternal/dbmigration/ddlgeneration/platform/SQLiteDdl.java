package io.ebeaninternal.dbmigration.ddlgeneration.platform;

import io.ebean.config.dbplatform.DatabasePlatform;
import io.ebeaninternal.dbmigration.ddlgeneration.DdlBuffer;
import io.ebeaninternal.dbmigration.ddlgeneration.DdlOptions;
import io.ebeaninternal.dbmigration.ddlgeneration.DdlWrite;
import io.ebeaninternal.dbmigration.migration.AlterColumn;

/**
 * SQLite platform specific DDL.
 * 
 * Note: SQLite has very limited alter capabilities. Altering a column is not
 * supported and may need a recreation of the whole table
 */
public class SQLiteDdl extends PlatformDdl {

  public SQLiteDdl(DatabasePlatform platform) {
    super(platform);
    this.identitySuffix = "";
    this.inlineForeignKeys = true;
  }

  @Override
  public void addTableComment(DdlBuffer apply, String tableName, String tableComment) {
    // not supported
  }

  @Override
  public void addColumnComment(DdlBuffer apply, String table, String column, String comment) {
    // not supported
  }

  @Override
  public String alterTableAddForeignKey(DdlOptions options, WriteForeignKey request) {
    return "-- not supported: " + super.alterTableAddForeignKey(options, request);
  }

  @Override
  public String alterTableDropForeignKey(String tableName, String fkName) {
    return "-- not supported: " + super.alterTableDropForeignKey(tableName, fkName);
  }

  @Override
  public String alterTableAddCheckConstraint(String tableName, String checkConstraintName, String checkConstraint) {
    return "-- not supported: " + super.alterTableAddCheckConstraint(tableName, checkConstraintName, checkConstraint);
  }

  @Override
  public String alterTableDropConstraint(String tableName, String constraintName) {
    return "-- not supported: " + super.alterTableDropConstraint(tableName, constraintName);
  }

  @Override
  public String alterTableAddUniqueConstraint(String tableName, String uqName, String[] columns, String[] nullableColumns) {
    return "-- not supported: " + super.alterTableAddUniqueConstraint(tableName, uqName, columns, nullableColumns);
  }

  @Override
  public String alterTableDropUniqueConstraint(String tableName, String uniqueConstraintName) {
    return "-- not supported: " + super.alterTableDropUniqueConstraint(tableName, uniqueConstraintName);
  }

  @Override
  public void alterColumnType(DdlWrite writer, AlterColumn alter) {
    writer.apply().append("-- not supported: ");
    super.alterColumnType(writer, alter);
  }

  @Override
  public void alterColumnDefault(DdlWrite writer, AlterColumn alter) {
    writer.apply().append("-- not supported: ");
    super.alterColumnDefault(writer, alter);
  }

  @Override
  public void alterColumnNotnull(DdlWrite writer, AlterColumn alter) {
    writer.apply().append("-- not supported: ");
    super.alterColumnNotnull(writer, alter);
  }

}
