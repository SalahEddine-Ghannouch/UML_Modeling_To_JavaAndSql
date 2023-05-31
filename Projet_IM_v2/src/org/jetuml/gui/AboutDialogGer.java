/*******************************************************************************
 * JetUML - A desktop application for fast UML diagramming.
 *
 * Copyright (C) 2020 by McGill University.
 *     
 * See: https://github.com/prmr/JetUML
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 *******************************************************************************/
package org.jetuml.gui;

import static org.jetuml.application.ApplicationResources.RESOURCES;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jetuml.JetUML;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.effect.BoxBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Border;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * A modal dialog that provides information about JetUML.
 */
public class AboutDialogGer {
	private final Stage aStage = new Stage();
	 
	
				/* added code Xml */
	static org.jdom2.Document document;
	
	
				/* added code Xml */
	/**
	 * Creates a new dialog.
	 * 
	 * @param pOwner The stage that owns this stage.
	 */
	public AboutDialogGer( Stage pOwner )
	{
		prepareStage(pOwner);
		aStage.setScene(createScene());
	}
	
	private void prepareStage(Stage pOwner) 
	{
		aStage.setResizable(false);
		aStage.initModality(Modality.WINDOW_MODAL);
		aStage.initOwner(pOwner);
		aStage.setTitle(String.format("%s %s", RESOURCES.getString("dialog.about.titleGer"),
				RESOURCES.getString("application.name")));
		aStage.getIcons().add(new Image(RESOURCES.getString("application.icon")));
	}
	
	private Scene createScene() 
	{
		final int verticalSpacing = 5;
		
		VBox info = new VBox(verticalSpacing);
		Text name = new Text(RESOURCES.getString("application.name"));
		name.setStyle("-fx-font-size: 15pt;");
		
		
		
		Text license = new Text(RESOURCES.getString("dialog.about.licenseGer"));
		license.setStyle("-fx-font-size: 18pt;");
		
		info.getChildren().addAll(name, license);
		
		final int padding = 15;
		HBox layout = new HBox(padding);
		layout.setStyle("-fx-background-color: gainsboro;");
		layout.setPadding(new Insets(padding));
		layout.setAlignment(Pos.CENTER_LEFT);
		
		ImageView logo = new ImageView(RESOURCES.getString("application.icon"));
		logo.setEffect(new BoxBlur());
		layout.getChildren().addAll(logo, info);
		layout.setAlignment(Pos.TOP_CENTER);
		
		aStage.requestFocus();
		aStage.addEventHandler(KeyEvent.KEY_PRESSED, pEvent -> 
		{
			if (pEvent.getCode() == KeyCode.ENTER) 
			{
				aStage.close();
			}
		});
		
		return new Scene(layout);
	}
	
	/**
	 * Shows the dialog and blocks the remainder of the UI
	 * until it is closed.
	 * @throws IOException 
	 * @throws JSONException 
	 */
	public void show() throws IOException, JSONException 
	{
        aStage.showAndWait();
        System.out.println("show");
        
        /* added code Xml */
        
        String title="";
    	File file = new File("NameOfFileSaved.txt"); // create a new file
    	try {
            // read the value from the file
            BufferedReader reader = new BufferedReader(new FileReader(file));
            title = reader.readLine();
            reader.close();
            System.out.println("Title : "+title);
            System.out.println();
    	} catch (IOException e) {
            e.printStackTrace();
        }
    	
    	
    	/* generate XML section */
    	
    	Element racine = new Element("diagrammeClasse");
//    	Scanner scanner = new Scanner(System.in);
    	//On crée un nouveau Document JDOM basé sur la racine que l'on vient de créer
    	document = new Document(racine);
    	
    	Element classes = new Element("classes");
		Element associations = new Element("associations");

		racine.addContent(classes);
		racine.addContent(associations);
		/* generate XML section */
		
        String jetData = new String(Files.readAllBytes(Paths.get(System.getProperty("user.dir")+"\\src\\org\\jetuml\\"+title)), StandardCharsets.UTF_8);

        JSONObject jetObject = new JSONObject(jetData);

        JSONArray nodesArray = jetObject.getJSONArray("nodes");

        List<ClassInfo> classInfoList = new ArrayList<>();

        for (int i = 0; i < nodesArray.length(); i++) {
            JSONObject nodeObject = nodesArray.getJSONObject(i);
            
            int id = nodeObject.getInt("id");
            String className = nodeObject.getString("name");
            String attributes = nodeObject.getString("attributes").split("\\\\")[0]; // get data before the first backslash
            String methods = nodeObject.getString("methods");
            
            
            /* generate XML section */
            Element classe = new Element("classe");
    		classes.addContent(classe);
    		
    		Attribute nameClasse1 = new Attribute("name",className);
			classe.setAttribute(nameClasse1);
			
			Attribute visibClasse1 = new Attribute("visibility","public");
			classe.setAttribute(visibClasse1);
			
			Element attributs = new Element("attributs");
			classe.addContent(attributs);
			String[] linesA = attributes.split("\n");
			if(linesA.length != 0) {
			for (String line : linesA) {
				
			    Element attrlineA = new Element("attribut");
			    attributs.addContent(attrlineA);
			    Attribute attrlineANV;
			    
			    int colonIndex = line.indexOf(":");
			    String beforeColon = line.substring(0, colonIndex).trim();
			    String afterColon = line.substring(colonIndex + 1).trim();
			    
			    char firstChar = beforeColon.charAt(0);
			    
			    if (firstChar == '+') {
			        beforeColon = beforeColon.substring(1).trim(); // Remove the '+' character
			         attrlineANV = new Attribute("visibility", "public");
			    } else if (firstChar == '-') {
			        beforeColon = beforeColon.substring(1).trim(); // Remove the '-' character
			         attrlineANV = new Attribute("visibility", "private");
			    } else if (firstChar == '*') {
			        beforeColon = beforeColon.substring(1).trim(); // Remove the '*' character
			         attrlineANV = new Attribute("visibility", "protected");
			    } else {
			    	 attrlineANV = new Attribute("visibility", "private");
			    }
			    
			    Element attrlineAN = new Element("name");
			    attrlineAN.setText(beforeColon);
			    attrlineA.addContent(attrlineAN);
			    
			    Attribute attrlineANA = new Attribute("type", afterColon);
			    attrlineAN.setAttribute(attrlineANA);
			    attrlineAN.setAttribute(attrlineANV);
			}

			}
	        
			Element methodes = new Element("methodes");
			classe.addContent(methodes);
			String[] linesM = methods.split("\n");
			if (linesM.length != 0) {
	        for (String line : linesM) {
	        	Element attrlineA = new Element("methode");
	        	methodes.addContent(attrlineA);
	        	Attribute attrlineANV;
	        	 
				int colonIndex = line.indexOf(":");
		        String beforeColon = line.substring(0, colonIndex).trim();
		        String afterColon = line.substring(colonIndex + 1).trim();
		        
		        char firstChar = beforeColon.charAt(0);
		        
		        if (firstChar == '+') {
			        beforeColon = beforeColon.substring(1).trim(); // Remove the '+' character
			         attrlineANV = new Attribute("visibility", "public");
			    } else if (firstChar == '-') {
			        beforeColon = beforeColon.substring(1).trim(); // Remove the '-' character
			         attrlineANV = new Attribute("visibility", "private");
			    } else if (firstChar == '*') {
			        beforeColon = beforeColon.substring(1).trim(); // Remove the '*' character
			         attrlineANV = new Attribute("visibility", "protected");
			    } else {
			    	 attrlineANV = new Attribute("visibility", "private");
			    }
		        
		        Element attrlineAN = new Element("name");
		        int indexA = beforeColon.indexOf("(");
		        String newBeforeColon = beforeColon.substring(0, indexA);
		        attrlineAN.setText(newBeforeColon);
		        attrlineA.addContent(attrlineAN);
		        
		        Attribute attrlineANA = new Attribute("typeR",afterColon);
		        attrlineAN.setAttribute(attrlineANA);
				attrlineAN.setAttribute(attrlineANV);
				
				Element parametres = new Element("parametres");
		        attrlineA.addContent(parametres);
		        int openParenIndex = beforeColon.indexOf("(");
		        int closeParenIndex = beforeColon.indexOf(")");
		        if (openParenIndex >= 0 && closeParenIndex >= 0 && closeParenIndex > openParenIndex) {
		            String insideParen = beforeColon.substring(openParenIndex + 1, closeParenIndex).trim();
		            if (!insideParen.isEmpty()) {
		            	Element paraA = new Element("parametre");
		            	parametres.addContent(paraA);
		            	String[] parts = insideParen.split(",");
		            	 for (String part : parts) {
		                     String trimmedPart = part.trim();
		                     String[] typeAndName = trimmedPart.split("\\s+");
		                     if (typeAndName.length == 2) {
		                         String type = typeAndName[0];
		                         String name = typeAndName[1];
		                         
		                         Element paraAN = new Element("name");
		                         paraAN.setText(name);
		                         paraA.addContent(paraAN);
		         		        
		                         Attribute paraANA = new Attribute("type",type);
		                         paraAN.setAttribute(paraANA);
		         				 		                         
		                     } else {
		                         System.out.println("Invalid variable declaration of method : " + trimmedPart);
		                     }
		                 }
					}
		        }
		        
				
	        }
	        
			}
			
			ClassInfo classInfo = new ClassInfo(id, className, attributes, methods);
            classInfoList.add(classInfo);
        }

        JSONArray edgesArray = jetObject.getJSONArray("edges");

        List<EdgeInfo> edgeInfoList = new ArrayList<>();

        for (int i = 0; i < edgesArray.length(); i++) {
            JSONObject edgeObject = edgesArray.getJSONObject(i);
            
            /*1 : column */
            int startId = -1;
            if (edgeObject.has("start")) {
                startId = edgeObject.getInt("start");
            }
            
            /*2 : column */
            int endId = -1;
            if (edgeObject.has("end")) {
            	endId = edgeObject.getInt("end");
            }
            
            /*3 : column */
            String startLabel = "khawi";
            if (edgeObject.has("startLabel")) {
            	startLabel = edgeObject.optString("startLabel");
            }
            
            /*4 : column */
            String middleLabel = "khawi";
            if (edgeObject.has("middleLabel")) {
            	middleLabel = edgeObject.optString("middleLabel");
            }
            
            /*5 : column */
            String directionality = "khawi";
            if (edgeObject.has("directionality")) {
            	directionality = edgeObject.getString("directionality");
            }
              
            /*6 : column */
            String endLabel = "khawi";
            if (edgeObject.has("endLabel")) {
            	endLabel = edgeObject.optString("endLabel");
            }
            
            /*7 : column */
            String typeedge = "khawi";
            if (edgeObject.has("type")) {
            	typeedge = edgeObject.optString("type");
            }
              
            /*8 : column */
            String generalization_type = "khawi";
            if (edgeObject.has("Generalization Type")) {
            	generalization_type = edgeObject.optString("Generalization Type");
            }
            
            /*9 : column */
            String aggregation_type = "khawi";
            if (edgeObject.has("Aggregation Type")) {
            	aggregation_type = edgeObject.optString("Aggregation Type");
            }
            String nodeName = null;
            String nodeNameEnd = null;

            
            /* generate XML section */
            
            Element associationA = new Element("association");
            associations.addContent(associationA);
            
            if (typeedge != "khawi") {
            
            
            String newString = typeedge.substring(0, typeedge.length() - 4);
            Attribute attrAss = new Attribute("type",newString);
            associationA.setAttribute(attrAss);
            
	        if (aggregation_type != "khawi" && aggregation_type.equals("Composition")) {
	        	Attribute genreComp = new Attribute("genre",aggregation_type);
	            associationA.setAttribute(genreComp);
			}
	        if (aggregation_type != "khawi" && aggregation_type.equals("Aggregation")){
	        	Attribute genreAgg = new Attribute("genre",aggregation_type);
	            associationA.setAttribute(genreAgg);
			}
	        
            Element nameAttA = new Element("name");
            nameAttA.setText(middleLabel);
            associationA.addContent(nameAttA);
        	
            
            for (int ii = 0; ii < nodesArray.length(); ii++) {
                JSONObject node = nodesArray.getJSONObject(ii);
                if (node.getInt("id") == startId) {
                    nodeName = node.getString("name");
//                    break;
                }
                if (node.getInt("id") == endId) {
                	nodeNameEnd = node.getString("name");
//                    break;
                }
            }
            // Loop through the edges array to find the edge with the matching start or end ID
            for (int iii = 0; iii < edgesArray.length(); iii++) {
                JSONObject edge = edgesArray.getJSONObject(iii);
                if (edge.getInt("start") == startId) {
                	
                	Element classD = new Element("classD");
                	classD.setText(nodeName);
                	associationA.addContent(classD);  
                	Attribute multD = new Attribute("multiplicity",startLabel);
                	classD.setAttribute(multD);
//                	break;
                }
                if (edge.getInt("end") == endId) {
                	
                	Element classA = new Element("classA");
                	classA.setText(nodeNameEnd);
                	associationA.addContent(classA); 
                	Attribute multA = new Attribute("multiplicity",endLabel);
                	classA.setAttribute(multA);
//                	break;
                }
            }
            
        	
        	
    		/* generate XML section */
            

            EdgeInfo edgeInfo = new EdgeInfo(startId, endId, startLabel, middleLabel, directionality, endLabel,typeedge,generalization_type,aggregation_type);
            edgeInfoList.add(edgeInfo);
        }
        }
        
        /* generate XML section */
        String repl = title.replace("class.jet", "xml");
        affiche();
		enregistre("src\\org\\jetuml\\aGenerateXml\\"+repl);
		/* generate XML section */
		
		
        // Do something with the class and edge information
        for (ClassInfo classInfo : classInfoList) {
            System.out.println("ID: " + classInfo.getId());
            System.out.println("Class Name: " + classInfo.getClassName());
            System.out.println("Attributes: \n" + classInfo.getAttributes());
            System.out.println("Methods: \n" + classInfo.getMethods());
            System.out.println();
        }

        for (EdgeInfo edgeInfo : edgeInfoList) {
        	
        	if (edgeInfo.getStartId() != -1) {
				System.out.println("Start ID: " + edgeInfo.getStartId());
			}
        	if (edgeInfo.getEndId() != -1) {
                System.out.println("End ID: " + edgeInfo.getEndId());
			}
        	if (edgeInfo.getStartLabel() != "khawi") {
        		System.out.println("Start Label: " + edgeInfo.getStartLabel());
			}
        	if (edgeInfo.getMiddleLabel() != "khawi") {
        		System.out.println("Middle Label: " + edgeInfo.getMiddleLabel());
			}
        	if (edgeInfo.getDirectionality() != "khawi") {
        		System.out.println("Directionality: " + edgeInfo.getDirectionality());
			}
        	if (edgeInfo.getEndLabel() != "khawi") {
        		System.out.println("End Label: " + edgeInfo.getEndLabel());
			}
        	if (edgeInfo.getTypeA() != "khawi") {
        		System.out.println("TypeAssociation: " + edgeInfo.getTypeA());
			}
        	if (edgeInfo.getGeneralization_type() != "khawi") {
        		System.out.println("Generalization Type: " + edgeInfo.getGeneralization_type());
			}
        	if (edgeInfo.getAggregation_type() != "khawi") {
        		System.out.println("Aggregation Type: " + edgeInfo.getAggregation_type());
			}
            
            
            
            System.out.println();
        }
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        /* added code Xml */
    }
	
	 /* added code Xml */
	public static class ClassInfo {
        private final int id;
        private final String className;
        private final String attributes;
        private final String methods;

        public ClassInfo(int id, String className, String attributes, String methods) {
            this.id = id;
            this.className = className;
            this.attributes = attributes;
            this.methods = methods;
        }

        public int getId() {
            return id;
        }

        public String getClassName() {
            return className;
        }

        public String getAttributes() {
            return attributes;
        }

        public String getMethods() {
            return methods;
        }
    }

    public static class EdgeInfo {
        private final int startId;
        private final int endId;
        private final String startLabel;
        private final String middleLabel;
        private final String directionality;
        private final String endLabel;
        private final String typeA;
        private final String generalization_type;
        private final String aggregation_type;

        public EdgeInfo(int startId, int endId, String startLabel, String middleLabel, String directionality, String endLabel,String typeA,String gtype,String atype) {
            this.startId = startId;
            this.endId = endId;
            this.startLabel = startLabel;
            this.middleLabel = middleLabel;
            this.directionality = directionality;
            this.endLabel = endLabel;
            this.typeA = typeA;
            this.generalization_type = gtype;
            this.aggregation_type = atype;
        }

        public int getStartId() {
            return startId;
        }

        public int getEndId() {
            return endId;
        }

        public String getStartLabel() {
            return startLabel;
        }

        public String getMiddleLabel() {
            return middleLabel;
        }

        public String getDirectionality() {
            return directionality;
        }

        public String getEndLabel() {
            return endLabel;
        }
        
        public String getTypeA() {
            return typeA;
        }
        
        public String getGeneralization_type() {
            return generalization_type;
        }
        
        public String getAggregation_type() {
            return aggregation_type;
        }
    }
    
    
	
	static void affiche() {
		try {
			//On utilise ici un affichage classique avec getPrettyFormat()
			XMLOutputter sortie = new XMLOutputter(Format.getPrettyFormat());
			sortie.output(document, System.out);
			
			}
			catch (java.io.IOException e){}
		}
	
	static void enregistre(String fichier){
			try{
				
			XMLOutputter sortie = new XMLOutputter(Format.getPrettyFormat());
			sortie.output(document, new FileOutputStream(fichier));
			}
			catch (java.io.IOException e){}
			}
	
	 /* added code Xml */
	
	
}
