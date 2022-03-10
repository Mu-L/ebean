package io.ebeaninternal.dbmigration.ddlgeneration.platform;

import io.ebean.config.DatabaseConfig;
import io.ebean.config.dbplatform.DatabasePlatform;
import io.ebean.config.dbplatform.DbPlatformType;
import io.ebeaninternal.dbmigration.ddlgeneration.DdlAlterTable;
import io.ebeaninternal.dbmigration.ddlgeneration.DdlBuffer;
import io.ebeaninternal.dbmigration.ddlgeneration.DdlHandler;
import io.ebeaninternal.dbmigration.ddlgeneration.DdlWrite;
import io.ebeaninternal.dbmigration.migration.AlterColumn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractHanaDdl extends PlatformDdl {

  private static final Pattern ARRAY_PATTERN = Pattern.compile("(\\w+)\\s*\\[\\s*\\]\\s*(\\(\\d+\\))?", Pattern.CASE_INSENSITIVE);

  public AbstractHanaDdl(DatabasePlatform platform) {
    super(platform);
    this.addColumn = "add";
    this.alterColumn = "alter";
    this.columnDropDefault = "default null";
    this.columnSetDefault = "default";
    this.columnSetNotnull = "not null";
    this.columnSetNull = " null";
    this.dropColumn = "drop";
    this.dropConstraintIfExists = "drop constraint ";
    this.dropIndexIfExists = "drop index ";
    this.dropSequenceIfExists = "drop sequence ";
    this.dropTableCascade = " cascade";
    this.dropTableIfExists = "drop table ";
    this.fallbackArrayType = "nvarchar(1000)";
    this.historyDdl = new HanaHistoryDdl();
    this.identitySuffix = " generated by default as identity";
  }

  @Override
  public void alterColumn(DdlWrite writer, AlterColumn alter) {
    String tableName = alter.getTableName();
    String columnName = alter.getColumnName();
    String currentType = alter.getCurrentType();
    String type = alter.getType() != null ? alter.getType() : currentType;
    type = convert(type);
    currentType = convert(currentType);
    boolean notnull = (alter.isNotnull() != null) ? alter.isNotnull() : Boolean.TRUE.equals(alter.isCurrentNotnull());
    String notnullClause = notnull ? " not null" : "";
    String defaultValue = DdlHelp.isDropDefault(alter.getDefaultValue()) ? "null"
      : (alter.getDefaultValue() != null ? alter.getDefaultValue() : alter.getCurrentDefaultValue());
    String defaultValueClause = (defaultValue == null || defaultValue.isEmpty()) ? "" : " default " + defaultValue;

    if (!isConvertible(currentType, type)) {
      // add an intermediate conversion if possible
      if (isNumberType(currentType)) {
        // numbers can always be converted to decimal
        alterTable(writer, tableName).append(alterColumn, columnName).append("decimal").append(notnullClause);

      } else if (isStringType(currentType)) {
        // strings can always be converted to nclob
        // Note: we do not add default clause here to avoid error[SAP DBTech JDBC: [336]: invalid default value:
        // default value cannot be created on column of data type NCLOB 
        alterTable(writer, tableName).append(alterColumn, columnName).append("nclob").append(notnullClause);
      }
    }

    alterTable(writer, tableName).append(alterColumn, columnName).append(type).append(defaultValueClause).append(notnullClause);
  }

  @Override
  public DdlHandler createDdlHandler(DatabaseConfig config) {
    return new HanaDdlHandler(config, this);
  }

  @Override
  protected String convertArrayType(String logicalArrayType) {
    Matcher matcher = ARRAY_PATTERN.matcher(logicalArrayType);
    if (matcher.matches()) {
      return convert(matcher.group(1)) + " array" + (matcher.group(2) == null ? "" : matcher.group(2));
    } else {
      return fallbackArrayType;
    }
  }

  @Override
  public String alterTableAddUniqueConstraint(String tableName, String uqName, String[] columns, String[] nullableColumns) {
    if (nullableColumns == null || nullableColumns.length == 0) {
      return super.alterTableAddUniqueConstraint(tableName, uqName, columns, nullableColumns);
    } else {
      return "-- cannot create unique index \"" + uqName + "\" on table \"" + tableName + "\" with nullable columns";
    }
  }

  @Override
  public String alterTableDropUniqueConstraint(String tableName, String uniqueConstraintName) {
    DdlBuffer buffer = new BaseDdlBuffer();

    buffer.append("delimiter $$").newLine();
    buffer.append("do").newLine();
    buffer.append("begin").newLine();
    buffer.append("declare exit handler for sql_error_code 397 begin end").endOfStatement();
    buffer.append("exec 'alter table ").append(tableName).append(" ").append(dropUniqueConstraint).append(" ")
      .append(maxConstraintName(uniqueConstraintName)).append("'").endOfStatement();
    buffer.append("end").endOfStatement();
    buffer.append("$$");
    return buffer.getBuffer();
  }

  @Override
  public String alterTableDropConstraint(String tableName, String constraintName) {
    return alterTableDropUniqueConstraint(tableName, constraintName);
  }

  /**
   * It is rather complex to delete a column on HANA as there must not exist any
   * foreign keys. That's why we call a user stored procedure here
   */
  @Override
  public void alterTableDropColumn(DdlWrite writer, String tableName, String columnName) {
    alterTable(writer, tableName).raw("CALL usp_ebean_drop_column('").append(tableName).append("', '").append(columnName).append("')");
  }

  /**
   * Check if a data type can be converted to another data type. Data types can't
   * be converted if the target type has a lower precision than the source type.
   *
   * @param sourceType The source data type
   * @param targetType the target data type
   * @return {@code true} if the type can be converted, {@code false} otherwise
   */
  private boolean isConvertible(String sourceType, String targetType) {
    if (Objects.equals(sourceType, targetType)) {
      return true;
    }

    if (sourceType == null || targetType == null) {
      return true;
    }

    if ("bigint".equals(sourceType)) {
      if ("integer".equals(targetType) || "smallint".equals(targetType) || "tinyint".equals(targetType)) {
        return false;
      }
    } else if ("integer".equals(sourceType)) {
      if ("smallint".equals(targetType) || "tinyint".equals(targetType)) {
        return false;
      }
    } else if ("smallint".equals(sourceType)) {
      if ("tinyint".equals(targetType)) {
        return false;
      }
    } else if ("double".equals(sourceType)) {
      if ("real".equals(targetType)) {
        return false;
      }
    }

    DbPlatformType dbPlatformSourceType = DbPlatformType.parse(sourceType);

    if ("float".equals(dbPlatformSourceType.getName())) {
      if ("real".equals(targetType)) {
        return false;
      }
    } else if ("varchar".equals(dbPlatformSourceType.getName()) || "nvarchar".equals(dbPlatformSourceType.getName())) {
      DbPlatformType dbPlatformTargetType = DbPlatformType.parse(targetType);
      if ("varchar".equals(dbPlatformTargetType.getName()) || "nvarchar".equals(dbPlatformTargetType.getName())) {
        if (dbPlatformSourceType.getDefaultLength() > dbPlatformTargetType.getDefaultLength()) {
          return false;
        }
      }
    } else if ("decimal".equals(dbPlatformSourceType.getName())) {
      DbPlatformType dbPlatformTargetType = DbPlatformType.parse(targetType);
      if ("decimal".equals(dbPlatformTargetType.getName())) {
        if (dbPlatformSourceType.getDefaultLength() > dbPlatformTargetType.getDefaultLength()
          || dbPlatformSourceType.getDefaultScale() > dbPlatformTargetType.getDefaultScale()) {
          return false;
        }
      }
    }

    return true;
  }

  private boolean isNumberType(String type) {
    return type != null
      && ("bigint".equals(type) || "integer".equals(type) || "smallint".equals(type) || "tinyint".equals(type)
      || type.startsWith("float") || "real".equals(type) || "double".equals(type) || type.startsWith("decimal"));
  }

  private boolean isStringType(String type) {
    return type != null
      && (type.startsWith("varchar") || type.startsWith("nvarchar") || "clob".equals(type) || "nclob".equals(type));
  }

  @Override
  protected DdlAlterTable alterTable(DdlWrite writer, String tableName) {
    return writer.applyAlterTable(tableName, HanaAlterTableWrite::new);
  }

  /**
   * Joins alter table commands and add open/closing brackets for the alter statements
   */
  private static class HanaAlterTableWrite extends BaseAlterTableWrite {

    public HanaAlterTableWrite(String tableName) {
      super(tableName);
    }

    @Override
    protected List<AlterCmd> postProcessCommands(List<AlterCmd> cmds) {
      List<AlterCmd> newCmds = new ArrayList<>();
      Map<String, List<AlterCmd>> batches = new LinkedHashMap<>();
      Set<String> columns = new HashSet<>();
      for (AlterCmd cmd : cmds) {
        switch (cmd.getOperation()) {
        case "add":
        case "alter":
        case "drop":
          if (cmd.getColumn() != null && !columns.add(cmd.getColumn())) {
            // column already seen
            flushBatches(newCmds, batches);
            columns.clear();
          }
          batches.computeIfAbsent(cmd.getOperation(), k -> new ArrayList<>()).add(cmd);
          break;
        default:
          flushBatches(newCmds, batches);
          columns.clear();
          newCmds.add(cmd);
        }
      }
      flushBatches(newCmds, batches);
      return newCmds;
    }

    /**
     * Merges add/alter/drop commands into one statement.
     */
    private void flushBatches(List<AlterCmd> newCmds, Map<String, List<AlterCmd>> batches) {
      for (Entry<String, List<AlterCmd>> entry : batches.entrySet()) {
        AlterCmd raw = newRawCommand("alter table ").append(tableName()).append(" ")
          .append(entry.getKey()).append(" (");
        List<AlterCmd> cmds = entry.getValue();
        for (int i = 0; i < cmds.size(); i++) {
          AlterCmd cmd = cmds.get(i);
          if (i > 0) {
            raw.append(",\n   ");
          }
          raw.append(cmd.getColumn());
          if (!cmd.getAlternation().isEmpty()) {
            raw.append(" ").append(cmd.getAlternation());
          }
        }
        raw.append(")");
        newCmds.add(raw);
      }
      batches.clear();
    }
  }

}
