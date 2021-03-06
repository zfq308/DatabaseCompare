package fr.axione.dbcompare.parser.database;

import fr.axione.dbcompare.model.common.*;
import fr.axione.dbcompare.model.dbitem.*;
import fr.axione.dbcompare.model.dbitem.Package;
import fr.axione.dbcompare.parser.DatabaseFilter;

import java.sql.*;

/**
 * Created by jlesaux on 21/01/14.
 */
public class DatabaseStructure {

    DatabaseMetaData meta;
    DatabaseFilter filter;


    public Schema getSchema(Connection connection) throws SQLException {

        return getSchema(connection,new DatabaseFilter());

    }

    public Schema getSchema(Connection connection, DatabaseFilter databaseFilter) throws SQLException {
        Schema schema = null;
        meta  = connection.getMetaData();
        filter = databaseFilter;

        schema = new Schema();
        ResultSet schemaResult = meta.getSchemas();
        if (!schemaResult.isBeforeFirst() && schemaResult.getRow() == 0 ) {
            // get information from catalogue
            schemaResult.close();
            schemaResult = meta.getCatalogs();
            schemaResult.next();
            schema.setName(schemaResult.getString(1));

        }
        else {
            schemaResult.next();
            schema.setName(meta.getUserName());

        }

        schemaResult.close();


       // retreive the catalogue name
        ResultSet catalogueResult = meta.getCatalogs();
        while (catalogueResult.next()) {
            String catalogue = catalogueResult.getString(1);
            schema.setCatalog(catalogue);
        }
        catalogueResult.close();

        schema = getTables(schema);
        schema = getIndexes(schema);
        schema = getViews(schema);
        schema = getProcedures(schema);
        schema = getPackages(schema);

        connection.close();
        return schema;
    }

    protected Table setPrimaryKeys(Table table) throws SQLException {
        ResultSet primaryKeyResults = meta.getPrimaryKeys(null,null,table.getName());
        while (primaryKeyResults.next()){
            String columnName = primaryKeyResults.getString("COLUMN_NAME");
            if (table.getColumns().containsKey(columnName)) {
                table.getColumns().get(columnName).setIsPrimaryKey(true);
                String indexName = primaryKeyResults.getString("PK_NAME");
                Index pkIndex = new Index(table.getSchema());
                pkIndex.setName(indexName);

                pkIndex.getTypes().add(ConstraintType.PRIMARY_CONSTRAINT);
                pkIndex.getTypes().add(ConstraintType.UNIQUE_PLAIN_INDEX);
                pkIndex.getColumns().add(table.getColumns().get(columnName));
                table.getIndexes().put(pkIndex.getName(),pkIndex);
            }
        }
        primaryKeyResults.close();
        return table;
    }

    protected Table setForeignKeys(Table table) throws SQLException {
        ResultSet foreignKeyResults = meta.getExportedKeys(null, null, table.getName());
        while (foreignKeyResults.next()){
            String fkColumnName = foreignKeyResults.getString("FKCOLUMN_NAME");
            String fkTableName = foreignKeyResults.getString("FKTABLE_NAME");
            String pkTableName = foreignKeyResults.getString("PKTABLE_NAME");
            String pkColumnName = foreignKeyResults.getString("PKCOLUMN_NAME");
            String fkName = foreignKeyResults.getString("FK_NAME");
            if (table.getSchema().getTables().containsKey(fkTableName)) {
                Table fkTable = table.getSchema().getTables().get(fkTableName);
                if (fkTable.getColumns().containsKey(fkColumnName)) {
                    Column fkColumn = fkTable.getColumns().get(fkColumnName);

                    fkColumn.setIsForeignKey(true);
                    Index index = new Index(table.getSchema());
                    index.setName(fkName);
                    index.getColumns().add(fkColumn);
                    index.getTypes().add(ConstraintType.FOREIGN_KEY);
//                    Constraint constraint = new Constraint(table.getSchema());
//                    constraint.setName(fkName);
//                    constraint.setForeignColumn(fkColumn);
                    if  (table.getName().equals(pkTableName) && table.getColumns().containsKey(pkColumnName)) {
                      //  constraint.setPrimaryColumn(table.getColumns().get(pkColumnName));
                        index.getColumns().add(table.getColumns().get(pkColumnName));
                    }
                    fkTable.getIndexes().put(index.getName(),index);
                   // table.getIndexes().put(index.getName(),index);
                   // fkTable.getConstraints().put(constraint.getName(),constraint);
                   // table.getConstraints().put(constraint.getName(),constraint);
                   // table.getSchema().getConstraints().put(constraint.getName(),constraint);

                }
            }

        }
        foreignKeyResults.close();
        return table;
    }

    protected Table getColumns(Table table) throws SQLException {
        ResultSet columnResults = null;
        if (filter.getColumnPattern() != null) {
            columnResults = meta.getColumns(null,null,table.getName(),null);
        }
        else  {
            columnResults = meta.getColumns(null,null,table.getName(),filter.getColumnPattern());
        }

        while (columnResults.next()) {
            String colName = columnResults.getString("COLUMN_NAME");
            String colType = columnResults.getString("TYPE_NAME");
            String size =  columnResults.getString("COLUMN_SIZE");
            int colSize = columnResults.getInt("COLUMN_SIZE");
            int nullable = columnResults.getInt("NULLABLE");

             if ( colSize == 0 ) {
                 colSize = columnResults.getInt("CHAR_OCTET_LENGTH");
             }

            Column column = new Column(colName,table);
            column.setSize(Integer.valueOf(colSize));
            column.setType(getType(colType));
            column.setNullable(nullable == DatabaseMetaData.columnNullable ? true : false);
            table.getColumns().put(colName,column);
        }

        columnResults.close();


        table = setPrimaryKeys(table);
        table = setForeignKeys(table);

        return table;
    }

    protected Schema getTables(Schema schema) throws SQLException {

        //String[] TABLE_TYPES = {"TABLE"};
        ResultSet tablesResults = null;
        if ( filter.getTablePattern() != null) {
            tablesResults = meta.getTables(null,null,filter.getTablePattern(),new String[] {"TABLE"});
        }
        else {
            tablesResults = meta.getTables(null,null,"%",new String[] {"TABLE"});
        }

        while (tablesResults.next()){
            String schemaName = tablesResults.getString("TABLE_SCHEM");
            if ( schema.getName().equals(schemaName)) {

                Table table = new Table(tablesResults.getString("TABLE_NAME"),schema);
//                String type = tablesResults.getString("TABLE_TYPE");
//                String cat = tablesResults.getString("TABLE_CAT");
//                String schem = tablesResults.getString("TABLE_SCHEM");


//            ResultSet indexesResults = meta.getIndexInfo(null,null,table.getName(),false,true);
//            while (indexesResults.next()) {
//                int type = indexesResults.getShort("TYPE");
//                String indexName = indexesResults.getString("INDEX_NAME");
//                boolean isUniq = indexesResults.getBoolean("NON_UNIQUE");
//                String columnName = indexesResults.getString("COLUMN_NAME");
//                System.out.println(columnName);
//            }
                schema.getTables().put(table.getName(), table);
            }
        }

        tablesResults.close();


        // retrieve all tables :
        for (String tableName : schema.getTables().keySet()) {
            Table table = schema.getTables().get(tableName);
            table = getColumns(table);
            schema.getTables().put(tableName,table);
        }
        return schema;
    }

    protected Package getPackageCode(Package pack) throws SQLException {

        // to forgive only admin can select on this table
        String driverName = meta.getDriverName().toUpperCase();

        if (driverName.contains("ORACLE")) {
            Connection connection = meta.getConnection();
            PreparedStatement statement = connection.prepareStatement("select text from user_source where name=? and type = 'PACKAGE BODY' order by line ");
            statement.setString(1,pack.getName());
            ResultSet resultSet = statement.executeQuery();
            StringBuilder result = new StringBuilder();

            while (resultSet.next()) {
                result.append(resultSet.getString("text"));
            }
            if ( result != null ) {
                pack.setSqlCodeLoaded(true);
            }
            resultSet.close();
            pack.setSqlCode(result.toString());
        }

        return pack;
    }

    protected Schema getPackages(Schema schema) throws SQLException {
        for (String packName : schema.getPackages().keySet()) {
            Package pack = schema.getPackages().get(packName);
            pack = getPackageCode(pack);
            pack.setSchema(schema);
            schema.getPackages().put(packName,pack);
        }
        return schema;
    }

    protected Schema getProcedures(Schema schema) throws SQLException {

        // to get content use specific oracle query like : select text from user_source where name='PROCEDURE NAME';
        ResultSet proceduresSet = meta.getProcedures(schema.getCatalog(),schema.getName(),"%");
        while (proceduresSet.next()){
            String procedureCatalogue = proceduresSet.getString("PROCEDURE_CAT");
            String name = proceduresSet.getString("PROCEDURE_NAME");
            short type = proceduresSet.getShort("PROCEDURE_TYPE");
            String procedureSchema = proceduresSet.getString("PROCEDURE_SCHEM");
            String specificName = proceduresSet.getString("SPECIFIC_NAME");
            String procedureRemark = proceduresSet.getString("REMARKS");


            if (schema.getName().equals(procedureSchema)) {
                Procedure procedure;
                if (schema.getStoredProcedures().containsKey(name)){
                    procedure = schema.getStoredProcedures().get(name);
                }
                else {
                    procedure = new Procedure(schema);
                    procedure.setName(name);
                    procedure.setRemark(procedureRemark);
                    procedure.setProcedureType(getProcedureType(type));
                    procedure.setCatalogue(procedureCatalogue);
                    if (procedureRemark.equals("Packaged function")) {
                        procedure.setPackageItemType(PackageItemType.Function);
                    }
                    else if (procedureRemark.equals("Packaged procedure")) {
                        procedure.setPackageItemType(PackageItemType.Procedure);
                    }
                    else {
                        procedure.setPackageItemType(null);
                    }

                    //procedure.setPackageItemType();
                }

                ResultSet rs = meta.getProcedureColumns(schema.getCatalog(),
                        schema.getName(),
                        name,
                        "%");

  //              System.out.println(name + " " + type + " " + procedureSchema + " " + specificName + " " + procedureRemark);
                while(rs.next()) {

                    ProcedureColumn procedureColumn;

                    String procedureCatalog     = rs.getString("PROCEDURE_CAT");
                    String procedureName        = rs.getString("PROCEDURE_NAME");
                    String columnName           = rs.getString("COLUMN_NAME");
                    short  columnReturn         = rs.getShort("COLUMN_TYPE");
                    String columnDataType       = rs.getString("DATA_TYPE");
                    String columnReturnTypeName = rs.getString("TYPE_NAME");
                    int    columnPrecision      = rs.getInt("PRECISION");
                    int    columnByteLength     = rs.getInt("LENGTH");
                    short  columnScale          = rs.getShort("SCALE");
                    short  columnRadix          = rs.getShort("RADIX");
                    short  columnNullable       = rs.getShort("NULLABLE");
                    String columnRemarks        = rs.getString("REMARKS");

                    if (procedure.getColumns().containsKey(columnName)) {
                        procedureColumn = procedure.getColumns().get(columnName);
                    }
                    else {
                        procedureColumn = new ProcedureColumn();
                    }
                    procedureColumn.setName(columnName);
                    procedureColumn.setRemark(columnRemarks);
                    procedureColumn.setSize(columnByteLength == 0 ? 1 : columnByteLength);
                    procedureColumn.setType(getType(columnReturnTypeName));
                    procedureColumn.setProcedureColumnType(getColumnReturn(columnReturn));
                    procedureColumn.setNullable(columnNullable == 1 ? true : false);

                    procedure.getColumns().put(procedureColumn.getName(),procedureColumn);

/*                    // DatabaseMetaData.procedureColumnReturn == columnReturn ?

                    System.out.println("stored Procedure name="+procedureName);
                    System.out.println("procedureCatalog=" + procedureCatalog);
                    System.out.println("procedureSchema=" + procedureSchema);
                    System.out.println("procedureName=" + procedureName);
                    System.out.println("columnName=" + columnName);
                    System.out.println("columnReturn=" + getColumnReturn(columnReturn) );
                    System.out.println("columnDataType=" + columnDataType);
                    System.out.println("columnReturnTypeName=" + columnReturnTypeName);
                    System.out.println("columnPrecision=" + columnPrecision);
                    System.out.println("columnByteLength=" + columnByteLength);
                    System.out.println("columnScale=" + columnScale);
                    System.out.println("columnRadix=" + columnRadix);
                    System.out.println("columnNullable=" + columnNullable);
                    System.out.println("columnRemarks=" + columnRemarks);*/

                }
                rs.close();
                Package pack = null;
                if (procedureCatalogue == null) {
                    // for unclassified Procedure
                    procedureCatalogue ="STANDALONE";
                }
                if (schema.getPackages().containsKey(procedureCatalogue)) {
                    pack = schema.getPackages().get(procedureCatalogue);
                }
                else {
                    pack = new Package(schema);
                    pack.setName(procedureCatalogue);
                }

                pack.getProcedures().add(procedure);
                schema.getPackages().put(pack.getName(),pack);
                //schema.getStoredProcedures().put(procedure.getName(), procedure);*/
            }

        }
        proceduresSet.close();


        /*for (String key : schema.getStoredProcedures().keySet()){
            Procedure procedure = schema.getStoredProcedures().get(key);
            schema.getStoredProcedures().put(key,getProcedureCode(procedure));
        }*/

        return schema;
    }

    protected View getColumns(View view) throws SQLException {
        ResultSet columnResults = null;
        if (filter.getColumnPattern() != null) {
            columnResults = meta.getColumns(null,null,view.getName(),null);
        }
        else  {
            columnResults = meta.getColumns(null,null,view.getName(),filter.getColumnPattern());
        }

        /*ResultSetMetaData resultSetMetaData = columnResults.getMetaData();
        for (int i = 1; i<resultSetMetaData.getColumnCount(); i++){
            System.out.println("View column : " + resultSetMetaData.getColumnName(i));
        }*/
        while (columnResults.next()) {
            String colName = columnResults.getString("COLUMN_NAME");
            String colType = columnResults.getString("TYPE_NAME");
            String size =  columnResults.getString("COLUMN_SIZE");
            int colSize = columnResults.getInt("COLUMN_SIZE");
            int nullable = columnResults.getInt("NULLABLE");
            String tableName = columnResults.getString("TABLE_NAME");

            if ( colSize == 0 ) {
                colSize = columnResults.getInt("CHAR_OCTET_LENGTH");
            }

            /*
            Table tableOwner = null;
            if( view.getSchema().getTables().containsKey(tableName) ) {
                tableOwner = view.getSchema().getTables().get(tableName);
            }
            else {
                tableOwner = new Table(tableName,view.getSchema());
                view.getSchema().getTables().put(tableOwner.getName(),tableOwner);
            }*/

        //    Column column = new Column(colName,tableOwner);

            Column column = new Column();
            column.setName(colName);
            column.setSize(Integer.valueOf(colSize));
            column.setType(getType(colType));
            column.setNullable(nullable == DatabaseMetaData.columnNullable ? true : false);

            view.getColumns().put(colName, column);
            //tableOwner.getColumns().put(colName,column);
        }

        columnResults.close();

        return view;
    }

    protected Schema getViews(Schema schema) throws SQLException {
        ResultSet viewsResults = null;
        if ( filter.getTablePattern() != null) {
            viewsResults = meta.getTables(null,null,filter.getTablePattern(),new String[] {"VIEW"});
        }
        else {
            viewsResults = meta.getTables(null,null,"%",new String[] {"VIEW"});
        }

        while (viewsResults.next()){
            String schemaName = viewsResults.getString("TABLE_SCHEM");
            if ( schema.getName().equals(schemaName)) {

                View view = new View(viewsResults.getString("TABLE_NAME"),schema);
                schema.getViews().put(view.getName(),view);
            }
        }

        viewsResults.close();

        for (String viewName : schema.getViews().keySet()) {
            View view = schema.getViews().get(viewName);
            view = getColumns(view);
            schema.getViews().put(viewName,view);
        }

        return schema;

    }

    protected  Schema getIndexes(Schema schema) throws SQLException {

        for (String tableName : schema.getTables().keySet()) {
            // récupération des contraintes uniques
            ResultSet indexesResults = null;

            indexesResults = meta.getIndexInfo(schema.getCatalog(),schema.getName(),tableName,true,true);

            while (indexesResults.next()) {
                String indexName = indexesResults.getString("INDEX_NAME");
                short indexType = indexesResults.getShort("TYPE");
                String columnName = indexesResults.getString("COLUMN_NAME");
                Index index;
                if (indexName != null) {
                    Table table = schema.getTables().get(tableName);
                    if (table.getIndexes().containsKey(indexName)) {
                        index = table.getIndexes().get(indexName);
                    }
                    else {
                        index = new Index(schema);
                        index.setName(indexName);
                        index.getTypes().add(ConstraintType.UNIQUE_CONSTRAINT);
                        index.getTypes().add(ConstraintType.UNIQUE_PLAIN_INDEX);
                        table.getIndexes().put(index.getName(),index);
                    }

                    if (table.getColumns().containsKey(columnName)) {
                        Column column = table.getColumns().get(columnName);
                        index.getColumns().add(column);
                    }
                    else {
                        System.out.println("Table:" + tableName + " does not contain column " + columnName);
                    }

                }


            }
            indexesResults.close();

        }




//        for (String tableName : schema.getTables().keySet()) {
//            // récupération des contraintes uniques
//            ResultSet indexesResults = null;
//
//            indexesResults = meta.getIndexInfo(schema.getCatalog(),schema.getName(),tableName,false,true);
//            while (indexesResults.next()) {
//                String indexName = indexesResults.getString("INDEX_NAME");
//                short indexType = indexesResults.getShort("TYPE");
//                String columnName = indexesResults.getString("COLUMN_NAME");
//                Index index ;
//                if (indexName != null) {
//                    Table table = schema.getTables().get(tableName);
//                    if (table.getIndexes().containsKey(indexName)) {
//                        index = table.getIndexes().get(indexName);
//                    }
//                    else {
//                        index = new Index(schema);
//                        index.setName(indexName);
//                        index.setType(ConstraintType.UNIQUE_PLAIN_INDEX);
//                        table.getIndexes().put(index.getName(),index);
//                    }
//
//                    if (table.getColumns().containsKey(columnName)) {
//                        Column column = table.getColumns().get(columnName);
//                        index.getColumns().add(column);
//                    }
//                    else {
//                        System.out.println("Table:" + tableName + " does not contain column " + columnName);
//                    }
//
//                }
//
//            }
//            indexesResults.close();
//
//        }

        return schema;
    }


    protected ColumnType getType(String type) {
        ColumnType ctype =  ColumnType.UNKNOWN;
        try {
            ctype = ColumnType.valueOf(type.replace(' ','_').replace('/','_'));
        } catch (IllegalArgumentException e) {
            ctype = ColumnType.UNKNOWN;
        }
        return ctype;
    }

    protected ProcedureColumnType getColumnReturn(short in) {
        switch (in) {
            case DatabaseMetaData.procedureColumnIn : return ProcedureColumnType.PROCEDURECOLUMNIN;
            case DatabaseMetaData.procedureColumnInOut : return ProcedureColumnType.PROCEDURECOLUMNINOUT;
            case DatabaseMetaData.procedureColumnOut : return ProcedureColumnType.PROCEDURECOLUMNOUT;
            case DatabaseMetaData.procedureColumnResult : return ProcedureColumnType.PROCEDURECOLUMNRESULT;
            case DatabaseMetaData.procedureColumnReturn : return ProcedureColumnType.PROCEDURECOLUMNRETURN;
            case DatabaseMetaData.procedureColumnUnknown : return ProcedureColumnType.PROCEDURECOLUMNUNKNOWN;
            default : return ProcedureColumnType.PROCEDURECOLUMNUNKNOWN;
        }
    }
    protected ProcedureType getProcedureType(short in ){
        switch (in) {
            case DatabaseMetaData.procedureResultUnknown : return ProcedureType.PROCEDURERESULTUNKNOWN;
            case DatabaseMetaData.procedureReturnsResult : return ProcedureType.PROCEDURERETURNSRESULT;
            case DatabaseMetaData.procedureNoResult : return ProcedureType.PROCEDURENORESULT;
            default : return ProcedureType.PROCEDURERESULTUNKNOWN;
        }
    }

}
