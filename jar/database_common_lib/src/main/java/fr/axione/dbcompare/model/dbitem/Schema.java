package fr.axione.dbcompare.model.dbitem;

import fr.axione.dbcompare.analyse.Direction;
import fr.axione.dbcompare.analyse.Report;
import fr.axione.dbcompare.analyse.ReportItem;

import java.util.HashMap;

/**
 * Created by jlesaux on 20/01/14.
 */
public class Schema extends Report {

    String name;
    String catalog;
    HashMap<String, Table> tables;
    HashMap<String, Trigger> triggers;
    HashMap<String, View> views;
    //HashMap<String, Constraint> constraints;

    public Schema() {
        tables = new HashMap<String, Table>();
        triggers = new HashMap<String, Trigger>();
        views = new HashMap<String, View>();
       // constraints = new HashMap<String, Constraint>();

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public HashMap<String, Table> getTables() {
        return tables;
    }

    public void setTables(HashMap<String, Table> tables) {
        this.tables = tables;
    }

    public HashMap<String, Trigger> getTriggers() {
        return triggers;
    }

    public void setTriggers(HashMap<String, Trigger> triggers) {
        this.triggers = triggers;
    }

    public HashMap<String, View> getViews() {
        return views;
    }

    public void setViews(HashMap<String, View> views) {
        this.views = views;
    }

//    public HashMap<String, Constraint> getConstraints() {
//        return constraints;
//    }
//
//    public void setConstraints(HashMap<String, Constraint> constraints) {
//        this.constraints = constraints;
//    }


    public String getCatalog() {
        return catalog;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }

    @Override
    public boolean equals(Object obj) {
        String objType = "Schema " + this.name;
        Boolean areEquals = true;

        if (obj == null ) {
            ReportItem report = new ReportItem();
            getErrors().add(report.fillWithInformations(objType,
                    obj,
                    this,
                    Direction.plus,
                    this.getClass().getName(),
                    objType + " : right schema is absent."));
            return false;
        }

        Schema rightSchema = (Schema)obj;

        if (this.name != null && ! this.name.equals(rightSchema.getName())) {
            ReportItem report = new ReportItem();
            getErrors().add(report.fillWithInformations(objType,
                    obj,
                    this,
                    Direction.plus,
                    this.getClass().getName(),
                    objType + " : right schema has a different name (" + this.name + ","+ rightSchema.getName()+")."));
            areEquals = false;
        }

        for (String key : this.getTables().keySet()) {
            if ( rightSchema.getTables().containsKey(key) ) {
                Table rightTable = rightSchema.getTables().get(key);
                Table leftTable = this.getTables().get(key);
                if (leftTable.equals(rightTable)) {
                    // just to compare
                }
            }
            else {
                ReportItem report = new ReportItem();
                getErrors().add(report.fillWithInformations(objType,
                        obj,
                        this,
                        Direction.plus,
                        this.getClass().getName(),
                        objType + " : right schema as no table (" + key +",null)."));
                areEquals = false;
            }
        }

        for (String key : rightSchema.getTables().keySet()){
            if (!this.getTables().containsKey(key)) {
                ReportItem report = new ReportItem();
                getErrors().add(report.fillWithInformations(objType,
                        obj,
                        this,
                        Direction.minus,
                        this.getClass().getName(),
                        objType + " : left schema as no table (null,"+key+")."));
                areEquals = false;
            }
        }

        // gathering all errors from all objects

        for (String tableKey : this.getTables().keySet()) {
            Table table = this.getTables().get(tableKey);
            getErrors().addAll(table.getErrors());

            for (String columnKey : table.getColumns().keySet()) {
                Column column = table.getColumns().get(columnKey);
                getErrors().addAll(column.getErrors());
            }
        }

        for (String tableKey : rightSchema.getTables().keySet()) {
            Table table = rightSchema.getTables().get(tableKey);
            getErrors().addAll(table.getErrors());

            for (String columnKey : table.getColumns().keySet()) {
                Column column = table.getColumns().get(columnKey);
                getErrors().addAll(column.getErrors());
            }
        }



        return areEquals;

    }
}
