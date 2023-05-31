/*******************************************************************************
 * JetUML - A desktop application for fast UML diagramming.
 *
 * Copyright (C) 2020, 2021 by McGill University.
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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jetuml.JetUML;
import org.jetuml.application.FileExtensions;
import org.jetuml.application.RecentFilesQueue;
import org.jetuml.application.UserPreferences;
import org.jetuml.application.UserPreferences.BooleanPreference;
import org.jetuml.diagram.Diagram;
import org.jetuml.diagram.DiagramType;
import org.jetuml.gui.tips.TipDialog;
import org.jetuml.persistence.DeserializationException;
import org.jetuml.persistence.PersistenceService;
import org.jetuml.persistence.VersionedDiagram;
import org.json.JSONException;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

/**
 * The main frame that contains panes that contain diagrams.
 */
public class EditorFrame extends BorderPane
{
	private static final String KEY_LAST_EXPORT_DIR = "lastExportDir";
	private static final String KEY_LAST_SAVEAS_DIR = "lastSaveAsDir";
	private static final String KEY_LAST_IMAGE_FORMAT = "lastImageFormat";
	private static final String USER_MANUAL_URL = "https://www.linkedin.com/in/salah-eddine-ghannouch-21300b221/";
	
	private static final String[] IMAGE_FORMATS = validFormats("png", "jpg", "gif", "bmp");
	
	private Stage aMainStage;
	private RecentFilesQueue aRecentFiles = new RecentFilesQueue();
	private Menu aRecentFilesMenu;
	private WelcomeTab aWelcomeTab;
	
	/**
	 * Constructs a blank frame with a desktop pane but no diagram window.
	 * 
	 * @param pMainStage The main stage used by the UMLEditor
	 * @param pOpenWith An optional file to open the application with.
	 */
	public EditorFrame(Stage pMainStage, Optional<File> pOpenWith) 
	{
		aMainStage = pMainStage;
		aRecentFiles.deserialize(Preferences.userNodeForPackage(JetUML.class).get("recent", "").trim());

		MenuBar menuBar = new MenuBar();
		setTop(menuBar);
		
		TabPane tabPane = new TabPane();
//		tabPane.setTabDragPolicy(TabPane.TabDragPolicy.REORDER); // This JavaFX feature is too buggy to use at the moment see issue #455
		tabPane.getSelectionModel().selectedItemProperty().addListener((pValue, pOld, pNew) -> setMenuVisibility());
		setCenter( tabPane );

		List<NewDiagramHandler> newDiagramHandlers = createNewDiagramHandlers();
		createFileMenu(menuBar, newDiagramHandlers);
		createEditMenu(menuBar);
		createViewMenu(menuBar);
		createHelpMenu(menuBar);
		setMenuVisibility();
		
		aWelcomeTab = new WelcomeTab(newDiagramHandlers);
		showWelcomeTabIfNecessary();
		
		pOpenWith.ifPresent(this::open);
		
		setOnKeyPressed(e -> 
		{
			if( !isWelcomeTabShowing() && e.isShiftDown() )
			{
				getSelectedDiagramTab().shiftKeyPressed();
			}
		});
		setOnKeyTyped(e -> 
		{
			if( !isWelcomeTabShowing() && !e.isShortcutDown())
			{
				getSelectedDiagramTab().keyTyped(e.getCharacter());
			}
		});
	}
	
	/* Returns the subset of pDesiredFormats for which a registered image writer 
	 * claims to recognized the format */
	private static String[] validFormats(String... pDesiredFormats)
	{
		List<String> recognizedWriters = Arrays.asList(ImageIO.getWriterFormatNames());
		List<String> validFormats = new ArrayList<>();
		for( String format : pDesiredFormats )
		{
			if( recognizedWriters.contains(format))
			{
				validFormats.add(format);
			}
		}
		return validFormats.toArray(new String[validFormats.size()]);
	}
	
	/*
	 * Traverses all menu items up to the second level (top level
	 * menus and their immediate sub-menus), that have "true" in their user data,
	 * indicating that they should only be enabled if there is a diagram 
	 * present. Then, sets their visibility to the boolean value that
	 * indicates whether there is a diagram present.
	 * 
	 * This method assumes that any sub-menu beyond the second level (sub-menus of
	 * top menus) will NOT be diagram-specific.
	 */
	private void setMenuVisibility()
	{
			((MenuBar)getTop()).getMenus().stream() // All top level menus
				.flatMap(menu -> Stream.concat(Stream.of(menu), menu.getItems().stream())) // All menus and immediate sub-menus
				.filter( item -> Boolean.TRUE.equals(item.getUserData())) // Retain only diagram-relevant menu items
				.forEach( item -> item.setDisable(isWelcomeTabShowing()));
	}
	
	// Returns the new menu
	private void createFileMenu(MenuBar pMenuBar, List<NewDiagramHandler> pNewDiagramHandlers) 
	{
		MenuFactory factory = new MenuFactory(RESOURCES);
		
		// Special menu items whose creation can't be inlined in the factory call.
		Menu newMenu = factory.createMenu("file.new", false);
		for( NewDiagramHandler handler : pNewDiagramHandlers )
		{
			newMenu.getItems().add(factory.createMenuItem(handler.getDiagramType().getName().toLowerCase(), false, handler));
		}
		
		aRecentFilesMenu = factory.createMenu("file.recent", false);
		buildRecentFilesMenu();
		
		// Standard factory invocation
		pMenuBar.getMenus().add(factory.createMenu("file", false, 
				newMenu,
				factory.createMenuItem("file.open", false, event -> openFile()),
				aRecentFilesMenu,
				factory.createMenuItem("file.close", true, event -> close()),
				factory.createMenuItem("file.save", true, event -> save()),
				factory.createMenuItem("file.save_as", true, event -> saveAs()),
				factory.createMenuItem("file.duplicate", true, event -> duplicate()),
				factory.createMenuItem("file.export_image", true, event -> exportImage()),
				factory.createMenuItem("file.generate_source_code", true, event -> {
					try {
						generateSourceCode();
					} catch (JDOMException | IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}),
				factory.createMenuItem("file.generate_xml", true, event -> {
					try {
						new AboutDialogGer(aMainStage).show();
					} catch (IOException | JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}),
				factory.createMenuItem("file.copy_to_clipboard", true, event -> copyToClipboard()),
				new SeparatorMenuItem(),
				factory.createMenuItem("file.exit", false, event -> exit())));
	}
	
	private void createEditMenu(MenuBar pMenuBar) 
	{
		MenuFactory factory = new MenuFactory(RESOURCES);
		pMenuBar.getMenus().add(factory.createMenu("edit", true, 
				factory.createMenuItem("edit.undo", true, pEvent -> getSelectedDiagramTab().undo()),
				factory.createMenuItem("edit.redo", true, pEvent -> getSelectedDiagramTab().redo()),
				factory.createMenuItem("edit.selectall", true, pEvent -> getSelectedDiagramTab().selectAll()),
				factory.createMenuItem("edit.properties", true, pEvent -> getSelectedDiagramTab().editSelected()),
				factory.createMenuItem("edit.cut", true, pEvent -> getSelectedDiagramTab().cut()),
				factory.createMenuItem("edit.paste", true, pEvent -> getSelectedDiagramTab().paste()),
				factory.createMenuItem("edit.copy", true, pEvent -> getSelectedDiagramTab().copy()),
				factory.createMenuItem("edit.delete", true, pEvent -> getSelectedDiagramTab().removeSelected())));

	}
	
	private void createViewMenu(MenuBar pMenuBar) 
	{
		MenuFactory factory = new MenuFactory(RESOURCES);
		pMenuBar.getMenus().add(factory.createMenu("view", false, 
				
				factory.createCheckMenuItem("view.show_grid", false, 
				UserPreferences.instance().getBoolean(BooleanPreference.showGrid), 
					pEvent -> UserPreferences.instance().setBoolean(BooleanPreference.showGrid, 
							((CheckMenuItem) pEvent.getSource()).isSelected())),
			
				factory.createCheckMenuItem("view.show_hints", false, 
				UserPreferences.instance().getBoolean(BooleanPreference.showToolHints),
				pEvent -> UserPreferences.instance().setBoolean(BooleanPreference.showToolHints, 
						((CheckMenuItem) pEvent.getSource()).isSelected())),
				
				factory.createCheckMenuItem("view.verbose_tooltips", false, 
						UserPreferences.instance().getBoolean(BooleanPreference.verboseToolTips),
						pEvent -> UserPreferences.instance().setBoolean(BooleanPreference.verboseToolTips, 
								((CheckMenuItem) pEvent.getSource()).isSelected())),
				
				factory.createCheckMenuItem("view.autoedit_node", false, 
						UserPreferences.instance().getBoolean(BooleanPreference.autoEditNode),
						event -> UserPreferences.instance().setBoolean(BooleanPreference.autoEditNode, 
								((CheckMenuItem) event.getSource()).isSelected())),
		
				factory.createMenuItem("view.diagram_size", false, event -> new DiagramSizeDialog(aMainStage).show()),
				factory.createMenuItem("view.font_size", false, event -> new FontSizeDialog(aMainStage).show()),
				factory.createMenuItem("view.zoom_in", true, event -> getSelectedDiagramTab().zoomIn()),
				factory.createMenuItem("view.zoom_out", true, event -> getSelectedDiagramTab().zoomOut()),
				factory.createMenuItem("view.reset_zoom", true, event -> getSelectedDiagramTab().resetZoom())));
	}
	
	private void createHelpMenu(MenuBar pMenuBar) 
	{
		MenuFactory factory = new MenuFactory(RESOURCES);
		pMenuBar.getMenus().add(factory.createMenu("help", false,
//				factory.createMenuItem("help.tips", false, event -> new TipDialog(aMainStage).show()),
				factory.createMenuItem("help.guide", false, event -> JetUML.openBrowser(USER_MANUAL_URL)),
				factory.createMenuItem("help.about", false, event -> new AboutDialog(aMainStage).show())));
	}
	
	/*
	 * @return The diagram tab whose corresponding file is pFile,
	 * or empty if there are none.
	 */
	private Optional<DiagramTab> findTabFor(File pFile)
	{
		for( Tab tab : tabs() )
		{
			if(tab instanceof DiagramTab)
			{	
				if(((DiagramTab) tab).getFile().isPresent()	&& 
						((DiagramTab) tab).getFile().get().getAbsoluteFile().equals(pFile.getAbsoluteFile())) 
				{
					return Optional.of((DiagramTab)tab);
				}
			}
		}
		return Optional.empty();
	}
	
	/*
	 * Opens a file with the given name, or switches to the frame if it is already
	 * open.
	 * 
	 * @param pName the file to open. Not null.
	 */
	private void open(File pFile) 
	{
		assert pFile != null;
		Optional<DiagramTab> tab = findTabFor(pFile);
		if( tab.isPresent() )
		{
			tabPane().getSelectionModel().select(tab.get());
			addRecentFile(pFile.getPath());
			return;
		}
		
		try 
		{
			VersionedDiagram versionedDiagram = PersistenceService.read(pFile); 
			DiagramTab frame = new DiagramTab(versionedDiagram.diagram());
			frame.setFile(pFile.getAbsoluteFile());
			addRecentFile(pFile.getPath());
			insertGraphFrameIntoTabbedPane(frame);
			if( versionedDiagram.wasMigrated())
			{
				String message = String.format(RESOURCES.getString("warning.version.message"), 
						versionedDiagram.version().toString());
				Alert alert = new Alert(AlertType.WARNING, message, ButtonType.OK);
				alert.setTitle(RESOURCES.getString("warning.version.title"));
				alert.initOwner(aMainStage);
				alert.showAndWait();
			}
		}
		catch(IOException | DeserializationException exception) 
		{
			Alert alert = new Alert(AlertType.ERROR, RESOURCES.getString("error.open_file"), ButtonType.OK);
			alert.initOwner(aMainStage);
			alert.showAndWait();
		}
	}
	
	private List<NamedHandler> getOpenFileHandlers()
	{
		List<NamedHandler> result = new ArrayList<>();
		for( File file : aRecentFiles )
   		{
			result.add(new NamedHandler(file.getName(), pEvent -> open(file)));
   		}
		return Collections.unmodifiableList(result);
	}
	
	private List<NewDiagramHandler> createNewDiagramHandlers()
	{
		List<NewDiagramHandler> result = new ArrayList<>();
		for( DiagramType diagramType : DiagramType.values() )
		{
			result.add(new NewDiagramHandler(diagramType, pEvent ->
			{
				insertGraphFrameIntoTabbedPane(new DiagramTab(new Diagram(diagramType)));
			}));
		}
		return Collections.unmodifiableList(result);
	}

	/*
	 * Adds a file name to the "recent files" list and rebuilds the "recent files"
	 * menu.
	 * 
	 * @param pNewFile the file name to add
	 */
	private void addRecentFile(String pNewFile) 
	{
		aRecentFiles.add(pNewFile);
		buildRecentFilesMenu();
	}
	
   	/*
   	 * Rebuilds the "recent files" menu. Only works if the number of
   	 * recent files is less than 10. Otherwise, additional logic will need
   	 * to be added to 0-index the mnemonics for files 1-9.
   	 */
   	private void buildRecentFilesMenu()
   	{ 
   		aRecentFilesMenu.getItems().clear();
   		aRecentFilesMenu.setDisable(!(aRecentFiles.size() > 0));
   		int i = 1;
   		for( File file : aRecentFiles )
   		{
   			String name = "_" + i + " " + file.getName();
   			MenuItem item = new MenuItem(name);
   			aRecentFilesMenu.getItems().add(item);
   			item.setOnAction(pEvent -> open(file));
            i++;
   		}
   }

	private void openFile() 
	{
		FileChooser fileChooser = new FileChooser();
		fileChooser.setInitialDirectory(aRecentFiles.getMostRecentDirectory());
		fileChooser.getExtensionFilters().addAll(FileExtensions.all());

		File selectedFile = fileChooser.showOpenDialog(aMainStage);
		if(selectedFile != null) 
		{
			open(selectedFile);
		}
	}

	/**
	 * Copies the current image to the clipboard.
	 */
	public void copyToClipboard() 
	{
		DiagramTab frame = getSelectedDiagramTab();
		final Image image = frame.createImage();
		final Clipboard clipboard = Clipboard.getSystemClipboard();
	    final ClipboardContent content = new ClipboardContent();
	    content.putImage(image);
	    clipboard.setContent(content);
		Alert alert = new Alert(AlertType.INFORMATION, RESOURCES.getString("dialog.to_clipboard.message"), ButtonType.OK);
		alert.initOwner(aMainStage);
		alert.setHeaderText(RESOURCES.getString("dialog.to_clipboard.title"));
		alert.showAndWait();
	}

	/* @pre there is a selected diagram tab, not just the welcome tab */
	private DiagramTab getSelectedDiagramTab()
	{
		Tab tab = ((TabPane) getCenter()).getSelectionModel().getSelectedItem();
		assert tab instanceof DiagramTab; // implies a null check.
		return (DiagramTab) tab;
	}

	private void close() 
	{
		DiagramTab diagramTab = getSelectedDiagramTab();
		// we only want to check attempts to close a frame
		if( diagramTab.hasUnsavedChanges() ) 
		{
			// ask user if it is ok to close
			Alert alert = new Alert(AlertType.CONFIRMATION, RESOURCES.getString("dialog.close.ok"), ButtonType.YES, ButtonType.NO);
			alert.initOwner(aMainStage);
			alert.setTitle(RESOURCES.getString("dialog.close.title"));
			alert.setHeaderText(RESOURCES.getString("dialog.close.title"));
			alert.showAndWait();

			if (alert.getResult() == ButtonType.YES) 
			{
				removeGraphFrameFromTabbedPane(diagramTab);
			}
			return;
		} 
		else 
		{
			removeGraphFrameFromTabbedPane(diagramTab);
		}
	}
	
	/**
	 * If a user confirms that they want to close their modified graph, this method
	 * will remove it from the current list of tabs.
	 * 
	 * @param pDiagramTab The current Tab that one wishes to close.
	 */
	public void close(DiagramTab pDiagramTab) 
	{
		if(pDiagramTab.hasUnsavedChanges()) 
		{
			Alert alert = new Alert(AlertType.CONFIRMATION, RESOURCES.getString("dialog.close.ok"), ButtonType.YES, ButtonType.NO);
			alert.initOwner(aMainStage);
			alert.setTitle(RESOURCES.getString("dialog.close.title"));
			alert.setHeaderText(RESOURCES.getString("dialog.close.title"));
			alert.showAndWait();

			if (alert.getResult() == ButtonType.YES) 
			{
				removeGraphFrameFromTabbedPane(pDiagramTab);
			}
		}
		else
		{
			removeGraphFrameFromTabbedPane(pDiagramTab);
		}
	}
	
	private void duplicate() 
	{
		insertGraphFrameIntoTabbedPane(new DiagramTab(getSelectedDiagramTab().getDiagram().duplicate()));
	}
	
	

	private void save() 
	{
		DiagramTab diagramTab = getSelectedDiagramTab();
		Optional<File> file = diagramTab.getFile();
		if(!file.isPresent()) 
		{
			saveAs();
			return;
		}
		try 
		{
			PersistenceService.save(diagramTab.getDiagram(), file.get());
			diagramTab.diagramSaved();
		} 
		catch(IOException exception) 
		{
			Alert alert = new Alert(AlertType.ERROR, RESOURCES.getString("error.save_file"), ButtonType.OK);
			alert.initOwner(aMainStage);
			alert.showAndWait();
		}
	}

	private void saveAs() 
	{
		DiagramTab diagramTab = getSelectedDiagramTab();
		Diagram diagram = diagramTab.getDiagram();

		FileChooser fileChooser = new FileChooser();
		fileChooser.getExtensionFilters().addAll(FileExtensions.all());
		fileChooser.setSelectedExtensionFilter(FileExtensions.forDiagramType(diagram.getType()));

		if(diagramTab.getFile().isPresent()) 
		{
			fileChooser.setInitialDirectory(diagramTab.getFile().get().getParentFile());
			fileChooser.setInitialFileName(diagramTab.getFile().get().getName());
		} 
		else 
		{
			fileChooser.setInitialDirectory(getLastDir(KEY_LAST_SAVEAS_DIR));
			fileChooser.setInitialFileName("");
		}

		try 
		{
			File result = fileChooser.showSaveDialog(aMainStage);
			if( result != null )
			{
				PersistenceService.save(diagram, result);
				addRecentFile(result.getAbsolutePath());
				diagramTab.setFile(result);
				diagramTab.setText(diagramTab.getFile().get().getName());
				diagramTab.diagramSaved();
				File dir = result.getParentFile();
				if( dir != null )
				{
					setLastDir(KEY_LAST_SAVEAS_DIR, dir);
				}
			}
		} 
		catch (IOException exception) 
		{
			Alert alert = new Alert(AlertType.ERROR, RESOURCES.getString("error.save_file"), ButtonType.OK);
			alert.initOwner(aMainStage);
			alert.showAndWait();
		}
	}

	private static File getLastDir(String pKey)
	{
		String dir = Preferences.userNodeForPackage(JetUML.class).get(pKey, ".");
		File result = new File(dir);
		if( !(result.exists() && result.isDirectory()))
		{
			result = new File(".");
		}
		return result;
	}
	
	private static void setLastDir(String pKey, File pLastExportDir)
	{
		Preferences.userNodeForPackage(JetUML.class).put(pKey, pLastExportDir.getAbsolutePath().toString());
	}
	
	/**
	 * Exports the current graph to an image file.
	 */
	private void exportImage() 
	{
		FileChooser fileChooser = getImageFileChooser(getLastDir(KEY_LAST_EXPORT_DIR), 
				Preferences.userNodeForPackage(JetUML.class).get(KEY_LAST_IMAGE_FORMAT, "png"));
		File file = fileChooser.showSaveDialog(aMainStage);
		if(file == null) 
		{
			return;
		}

		String fileName = file.getPath();
		String format = fileName.substring(fileName.lastIndexOf(".") + 1);
		Preferences.userNodeForPackage(JetUML.class).put(KEY_LAST_IMAGE_FORMAT, format);
				
		File dir = file.getParentFile();
		if( dir != null )
		{
			setLastDir(KEY_LAST_EXPORT_DIR, dir);
		}
		DiagramTab frame = getSelectedDiagramTab();
		try (OutputStream out = new FileOutputStream(file)) 
		{
			BufferedImage image = getBufferedImage(frame); 
			if("jpg".equals(format))	// to correct the display of JPEG/JPG images (removes red hue)
			{
				BufferedImage imageRGB = new BufferedImage(image.getWidth(), image.getHeight(), Transparency.OPAQUE);
				Graphics2D graphics = imageRGB.createGraphics();
				graphics.drawImage(image, 0,  0, null);
				ImageIO.write(imageRGB, format, out);
				graphics.dispose();
			}
			else if("bmp".equals(format))	// to correct the BufferedImage type
			{
				BufferedImage imageRGB = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
				Graphics2D graphics = imageRGB.createGraphics();
				graphics.drawImage(image, 0, 0, Color.WHITE, null);
				ImageIO.write(imageRGB, format, out);
				graphics.dispose();
			}
			else
			{
				ImageIO.write(image, format, out);
			}
		} 
		catch(IOException exception) 
		{
			Alert alert = new Alert(AlertType.ERROR, RESOURCES.getString("error.save_file"), ButtonType.OK);
			alert.initOwner(aMainStage);
			alert.showAndWait();
		}
	}
	
	
	private void generateSourceCode() throws JDOMException, IOException 
	{

		//Load and read the XML file
		SAXBuilder Builder = new SAXBuilder();
		//Get the path to xml files
		
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
	    
    	String repl = title.replace("class.jet", "xml");
		File inputfile = new File("src\\org\\jetuml\\aGenerateXml\\"+repl);
		Document doc = Builder.build(inputfile);
		
		
		//Get the root of the file
		Element root = doc.getRootElement();
		
		//Get the list of classe and association of the classes and associations elements
		Element classes = root.getChild("classes");
		Element associations = root.getChild("associations");
		
		List<Element> classe = classes.getChildren();
		List<Element> association = associations.getChildren();
		
		//We create a list the will hold all the associations between classes
        List<ClassesRelation> associList = new ArrayList<ClassesRelation>();
        
		//We start with defining the association between claases
		for(int elem=0;elem<association.size();elem++){
            Element currentAssociation = association.get(elem);
            String AssociationType = currentAssociation.getAttribute("type").getValue();
            String AssociationName = currentAssociation.getChildText("name");
            String DepartClasse = currentAssociation.getChild("classD").getText();
            String ArraivalClasse = currentAssociation.getChild("classA").getText();
            String ArraivalClasseMultiplicity =  currentAssociation.getChild("classA").getAttribute("multiplicity").getValue();
            associList.add(new ClassesRelation(AssociationType, AssociationName, DepartClasse, ArraivalClasse,ArraivalClasseMultiplicity));
        }
		
		//This list will hold all the attributes elements of each classe
		List<Element> attributesList;
		//In case of inheritance this list will hold the Parent classe attributes
		List<Element> SuperattributesList;
		//This list will hold the methodes of each classe
		List<Element> methodelist;
		//This list will hold the parameters of each methodes
		List<Element> methodeparams;
		
		ArrayList<String> ConstAttributes = new ArrayList<String>();
		
		//For created tables
		ArrayList<String> CreatedTables = new ArrayList<String>();
		
		//This will hold the constrecteur code
		StringBuilder Constrecteur = new StringBuilder();
		
		//This is for defining if the departClasse has an assosiation with multiple object of the Arraival classe
		boolean ClasseMultipliciy = false;
		boolean Extended = false;
		StringBuilder SQL = new StringBuilder();
		
		//Loop over all the classes
		for(int i=0 ;i<classe.size(); i++){
			
			StringBuilder Aggr = new StringBuilder();
			StringBuilder Compo = new StringBuilder();
			StringBuilder Super =new StringBuilder();
			String SuperClasse = "";
			StringBuilder code = new StringBuilder();
			
			
			boolean IsIN = false;
			
			//Get the classes of the classes element
			Element currentClasse = classe.get(i);
			String repl22 = title.replace(".class.jet", "");
			
			//Get the visibility and classe name attrubutes
			Attribute visibility = currentClasse.getAttribute("visibility");
			Attribute name = currentClasse.getAttribute("name");
			
			//This list will hold the arraival classe and its multiplicity that we will use to define the constrecteur
			List<String> associationList = new ArrayList<String>();
			
			//Check if the current classe is a child classe
			for(int associaction=0;associaction<associList.size();associaction++) {
				ClassesRelation relationElement = associList.get(associaction);
				if(name.getValue().equals(relationElement.getDepartClasse())){
					IsIN = true;
					if(relationElement.getRelationType().equals("Generalization")){
						code.append("package org.jetuml.aGenerateJava.Diagram_"+repl22+";\n");
						code.append("\nimport java.util.*;\n");
						code.append(visibility.getValue() +" class "+name.getValue()+" extends "+relationElement.getArraivalClasse()+" {\n\n\t//Attributes \n");
						SuperClasse = relationElement.getArraivalClasse();
						Extended = true;
						if(!CreatedTables.contains(relationElement.getArraivalClasse())) {
						SQL.append("\nCREATE TABLE "+relationElement.getArraivalClasse()+" (\n"
								+ "\tID NUMBER PRIMARY KEY \n);\n\n");
						CreatedTables.add(relationElement.getArraivalClasse());
						}
						if(!CreatedTables.contains(name.getValue())) {
						SQL.append("\nCREATE TABLE "+name.getValue()+" (\n"
								+ "\tID NUMBER PRIMARY KEY,\n"
								+ "\tid_"+relationElement.getArraivalClasse()+" NUMBER,\n"
										+ "\tFOREIGN KEY (id_"+relationElement.getArraivalClasse()+") REFERENCES "+relationElement.getArraivalClasse()+"(ID)\n"
												+ ");\n\n");
						CreatedTables.add(name.getValue());
						}
						else {
							SQL.append("\n ALTER TABLE "+name.getValue()+" ADD(\n"
									+ "id_"+relationElement.getArraivalClasse()+" NUMBER,\n"
											+ "FOREIGN KEY (id_"+relationElement.getArraivalClasse()+")\n"
													+ "REFERENCES "+relationElement.getArraivalClasse()+"(ID)\n"
															+ ");\n");
						}
					}
				}
			}
			if(!Extended) {
				code.append("package org.jetuml.aGenerateJava.Diagram_"+repl22+";\n");
				code.append("\nimport java.util.*;\n");
				code.append(visibility.getValue() +" class "+name.getValue()+" {\n\n\t//Attributes \n");
			}
			
			//Check the multiplicity and the relation between classes
			for(int associaction=0;associaction<associList.size();associaction++) {
			ClassesRelation relationElement = associList.get(associaction);
			
			if(name.getValue().equals(relationElement.getDepartClasse())){
				IsIN = true;
				if(relationElement.getArraivalClasseMultiplicity().equals("*") || relationElement.getArraivalClasseMultiplicity().equals("0..*") || relationElement.getArraivalClasseMultiplicity().equals("1..*"))
					ClasseMultipliciy = true;
				else ClasseMultipliciy = false;
			//System.out.println("Avant Aggr"+SQL);
			//System.out.println("After");
			if(relationElement.getRelationType().equals("Aggregation") && ClasseMultipliciy == true){
				code.append("\tprivate List<"+relationElement.getArraivalClasse()+"> "+relationElement.getArraivalClasse().toLowerCase()+";\n");
				associationList.add(relationElement.getArraivalClasse());
				associationList.add(relationElement.getArraivalClasseMultiplicity());
				Aggr.append("\t\tthis."+relationElement.getArraivalClasse().toLowerCase()+" = "+relationElement.getArraivalClasse().toLowerCase()+";\n");
				
				if(!CreatedTables.contains(relationElement.getArraivalClasse())) {
					SQL.append("\nCREATE TABLE "+relationElement.getArraivalClasse()+"(\n"
							+ "ID NUMBER PRIMARY KEY\n"
							+ ");\n\n");
					CreatedTables.add(relationElement.getArraivalClasse());
				}
				
				if(!CreatedTables.contains(relationElement.getDepartClasse())){
					SQL.append("\nCREATE TABLE "+relationElement.getDepartClasse()+"(\n"
							+ "ID NUMBER PRIMARY KEY,\n"
							+ "id_"+relationElement.getArraivalClasse()+" NUMBER,\n"
									+ "FOREIGN KEY (id_"+relationElement.getArraivalClasse()+") REFRENCES "+relationElement.getArraivalClasse()+"(ID) ON DELETE SET NULL\n"
											+ ");\n\n");
					CreatedTables.add(relationElement.getDepartClasse());
				
				}
				else {
					SQL.append("\nALTER TABLE "+relationElement.getDepartClasse()+"\n"
							+ "ADD fk_"+relationElement.getArraivalClasse()+" NUMBER;\n\n");
					SQL.append("ALTER TABLE "+relationElement.getDepartClasse()+"\n"
							+ "ADD FOREIGN KEY (fk_"+relationElement.getArraivalClasse()+") \n"
									+ "REFERENCES "+relationElement.getArraivalClasse()+" (ID) ON DELETE SET NULL;");
				}
				
				
			}
			else if(relationElement.getRelationType().equals("Aggregation") && ClasseMultipliciy == false) {
				code.append("\tprivate "+relationElement.getArraivalClasse()+" "+relationElement.getArraivalClasse().toLowerCase()+";\n");
				associationList.add(relationElement.getArraivalClasse());
				associationList.add(relationElement.getArraivalClasseMultiplicity());
				Aggr.append("\t\tthis."+relationElement.getArraivalClasse().toLowerCase()+" = "+relationElement.getArraivalClasse().toLowerCase()+";\n");
				
				if(!CreatedTables.contains(relationElement.getArraivalClasse())) {
					SQL.append("\nCREATE TABLE "+relationElement.getArraivalClasse()+"(\n"
							+ "ID NUMBER PRIMARY KEY\n"
							+ ");\n\n");
					CreatedTables.add(relationElement.getArraivalClasse());
				}
				
				if(!CreatedTables.contains(relationElement.getDepartClasse())){
					SQL.append("\nCREATE TABLE "+relationElement.getDepartClasse()+"(\n"
							+ "ID NUMBER PRIMARY KEY,\n"
							+ "id_"+relationElement.getArraivalClasse()+" NUMBER,\n"
									+ "FOREIGN KEY (id_"+relationElement.getArraivalClasse()+") REFRENCES "+relationElement.getArraivalClasse()+"(ID) ON DELETE SET NULL\n"
											+ ");\n\n");
					CreatedTables.add(relationElement.getDepartClasse());
				
				}
				else {
					SQL.append("\nALTER TABLE "+relationElement.getDepartClasse()+"\n"
							+ "ADD fk_"+relationElement.getArraivalClasse()+" NUMBER;\n\n");
					SQL.append("ALTER TABLE "+relationElement.getDepartClasse()+"\n"
							+ "ADD FOREIGN KEY (fk_"+relationElement.getArraivalClasse()+") \n"
									+ "REFERENCES "+relationElement.getArraivalClasse()+" (ID) ON DELETE SET NULL;");
				}
				
				}
			else if(relationElement.getRelationType().equals("Composition")) {
				if(ClasseMultipliciy) {
					code.append("\tprivate List<"+relationElement.getArraivalClasse()+"> "+relationElement.getArraivalClasse().toLowerCase()+";\n");
					Compo.append("\t\tthis."+relationElement.getArraivalClasse().toLowerCase()+" = new Arraylist<"+relationElement.getArraivalClasse()+">;\n");
				}
				else {
					code.append("\tprivate "+relationElement.getArraivalClasse()+" "+relationElement.getArraivalClasse().toLowerCase()+";\n");
					Compo.append("\t\tthis."+relationElement.getArraivalClasse().toLowerCase()+" = new "+relationElement.getArraivalClasse()+"();\n");
				}
				
				if(!CreatedTables.contains(relationElement.getArraivalClasse())) {
					SQL.append("\nCREATE TABLE "+relationElement.getArraivalClasse()+"(\n"
							+ "ID NUMBER PRIMARY KEY\n"
							+ ");\n\n");
					CreatedTables.add(relationElement.getArraivalClasse());
				}
				
				if(!CreatedTables.contains(relationElement.getDepartClasse())){
					SQL.append("\nCREATE TABLE "+relationElement.getDepartClasse()+"(\n"
							+ "ID NUMBER PRIMARY KEY,\n"
							+ "id_"+relationElement.getArraivalClasse()+" NUMBER,\n"
									+ "FOREIGN KEY (id_"+relationElement.getArraivalClasse()+") REFRENCES "+relationElement.getArraivalClasse()+"(ID) ON DELETE CASCADE\n"
											+ ");\n\n");
					CreatedTables.add(relationElement.getDepartClasse());
				
				}
				else {
					SQL.append("\nALTER TABLE "+relationElement.getDepartClasse()+"\n"
							+ "ADD fk_"+relationElement.getArraivalClasse()+" NUMBER;\n\n");
					SQL.append("ALTER TABLE "+relationElement.getDepartClasse()+"\n"
							+ "ADD FOREIGN KEY (fk_"+relationElement.getArraivalClasse()+") \n"
									+ "REFERENCES "+relationElement.getArraivalClasse()+" (ID) ON DELETE CASCADE;");
				}
				
			}
			}
			}
			if(!IsIN){
				IsIN = false;
				code = new StringBuilder();
				code.append("package org.jetuml.aGenerateJava.Diagram_"+repl22+";\n");
				code.append("\nimport java.util.*;\n");
				code.append(visibility.getValue() +" class "+name.getValue()+" {\n\n\t//Attributes \n");
				if(!CreatedTables.contains(name.getValue())) {
				SQL.append("\nCREATE TABLE "+name.getValue()+"(\n"
						+ "ID NUMBER PRIMARY KEY\n"
						+ ");\n\n");
				CreatedTables.add(name.getValue());
				}
			}
			//Get the attributes element
			Element attributes = currentClasse.getChild("attributs");
			
			//Get the attribut of attributes element
			attributesList = attributes.getChildren();
			
			if(attributesList.size() != 0) {
				SQL.append("\nALTER TABLE "+name.getValue()+"\n");
				SQL.append("\tADD (\n");
				for(int at=0; at<attributesList.size(); at++) {
					String type = attributesList.get(at).getChild("name").getAttribute("type").getValue();
					if(at!=attributesList.size()-1) {
						switch (type) {
						case "String": 
							SQL.append(attributesList.get(at).getChildText("name")+" VARCHAR"+",\n");
							break;
						case "int":
						case "long":	
							SQL.append(attributesList.get(at).getChildText("name")+" NUMBER"+",\n");
							break;
						case "boolean": 
							SQL.append(attributesList.get(at).getChildText("name")+" boolean"+",\n");
							break;
						case "float":
						case "double":
							SQL.append(attributesList.get(at).getChildText("name")+" FLOAT"+",\n");
							break;
					}
					}
					else {
						switch (type) {
						case "String": 
							SQL.append(attributesList.get(at).getChildText("name")+" VARCHAR"+"\n);\n");
							break;
						case "int":
						case "long":	
							SQL.append(attributesList.get(at).getChildText("name")+" NUMBER"+"\n);\n");
							break;
						case "boolean": 
							SQL.append(attributesList.get(at).getChildText("name")+" boolean"+"\n);\n");
							break;
						case "float":
						case "double":
							SQL.append(attributesList.get(at).getChildText("name")+" FLOAT"+"\n);\n");
							break;
					}
					}
				} 
			}
			
			//We start defining the constrecteur in diffrente cases
			Constrecteur.append("\n\t//Constrecteur\n\tpublic "+name.getValue()+"(");
			for(int j=0; j<attributesList.size(); j++) {
				Element attr = attributesList.get(j);
				Element attName = attr.getChild("name");
				Attribute attVisibility = attName.getAttribute("visibility");
				Attribute atttype = attName.getAttribute("type");
				code.append("\t"+attVisibility.getValue()+" "+atttype.getValue()+" "+attr.getChildText("name")+";\n");
				if(j!=attributesList.size()-1) Constrecteur.append(atttype.getValue()+" "+attr.getChildText("name")+", ");
				else {
					Constrecteur.append(atttype.getValue()+" "+attr.getChildText("name"));
					if(associationList.size() == 0 && SuperClasse.equals("")) {Constrecteur.append("){\n"); }
					else {
						if(!SuperClasse.equals("")){
							
							for(int l=0 ;l<classe.size(); l++) {
								Element supClasse = classe.get(l);
								if(supClasse.getAttribute("name").getValue().equals(SuperClasse)){
									Super.append("super(");
									Constrecteur.append(", ");
									SuperattributesList = supClasse.getChild("attributs").getChildren();
									for(int m=0; m<SuperattributesList.size(); m++) {
										Element Superattr = SuperattributesList.get(m);
										if(m!=SuperattributesList.size()-1) {
										Constrecteur.append(Superattr.getChild("name").getAttribute("type").getValue()+" "+Superattr.getChild("name").getText()+", ");
										Super.append(Superattr.getChild("name").getValue()+",");
										}
										else {Constrecteur.append(Superattr.getChild("name").getAttribute("type").getValue()+" "+Superattr.getChild("name").getText());
										Super.append(Superattr.getChild("name").getValue());
										}
									}
							}
						}
							for(int n=0;n<associList.size();n++) {
								if(associList.get(n).getDepartClasse().equals(SuperClasse) && associList.get(n).getRelationType().equals("Aggregation")) {
									Constrecteur.append(", ");
									Super.append(", ");
									if(associList.get(n).getArraivalClasseMultiplicity().equals("*") || associList.get(n).getArraivalClasseMultiplicity().equals("0..*") || associList.get(n).getArraivalClasseMultiplicity().equals("1..*")) {
										Constrecteur.append("List<"+associList.get(n).getArraivalClasse()+"> "+associList.get(n).getArraivalClasse().toLowerCase());
										Super.append(associList.get(n).getArraivalClasse().toLowerCase());
									}
									else {Constrecteur.append(associList.get(n).getArraivalClasse()+" "+associList.get(n).getArraivalClasse().toLowerCase());
										Super.append(associList.get(n).getArraivalClasse().toLowerCase());
									}
								}
							}
						}
						if(associationList.size() == 0) Constrecteur.append("){\n");
						else{
						Constrecteur.append(", ");
						for(int listelem=0; listelem<associationList.size();listelem+=2){
							if(listelem != associationList.size()-2) {
								if(associationList.get(listelem+1).equals("*") || associationList.get(listelem+1).equals("0..*") || associationList.get(listelem+1).equals("1..*")) {
									Constrecteur.append("List<"+associationList.get(listelem)+"> "+associationList.get(listelem).toLowerCase()+", ");
								}
								else Constrecteur.append(associationList.get(listelem)+" "+associationList.get(listelem).toLowerCase()+", ");
							}
							else {
								
								if(associationList.get(listelem+1).equals("*") || associationList.get(listelem+1).equals("0..*") || associationList.get(listelem+1).equals("1..*")) {
									Constrecteur.append("List<"+associationList.get(listelem)+"> "+associationList.get(listelem).toLowerCase()+"){\n");
								}
								else Constrecteur.append(associationList.get(listelem)+" "+associationList.get(listelem).toLowerCase()+"){\n");
								
							}
						}
					}
					}
					if(SuperClasse.equals(""))
					Constrecteur.append("\t\t"+Super+"\n");
					else
						Constrecteur.append("\t\t"+Super+");\n");
					}
				
				ConstAttributes.add(attr.getChildText("name"));
			}
			
			
			for(int constlist=0;constlist<ConstAttributes.size();constlist++) {
				Constrecteur.append("\t\tthis."+ConstAttributes.get(constlist)+" = "+ConstAttributes.get(constlist)+";\n");
			}
			if(Aggr.length() != 0 ) Constrecteur.append(Aggr);
			if(Compo.length() != 0) Constrecteur.append(Compo);
			
			
			//We start defining the methodes in each classe
			Constrecteur.append("\t}\n\n\t//methodes");
			code.append(Constrecteur);
			Constrecteur = new StringBuilder();
			ConstAttributes.clear();
			
			//Get the methodes inside the methodes element
			Element methodes = currentClasse.getChild("methodes");
			methodelist = methodes.getChildren();
			for(int k=0;k<methodelist.size();k++) {
				Element methode = methodelist.get(k);
				Element methodeR = methode.getChild("name");
				methodeparams = methode.getChild("parametres").getChildren();
				code.append("\n\t"+methodeR.getAttribute("visibility").getValue()+" "+methodeR.getAttribute("typeR").getValue()+" "+methodeR.getText()+"(");
				for(int param=0;param<methodeparams.size();param++) {
					Element parameter = methodeparams.get(param);
					String parType = parameter.getChild("name").getText();
					String parName = parameter.getChild("name").getAttribute("type").getValue();
					
					if(!parType.isEmpty() && !parName.isEmpty()) {
						if(param != methodeparams.size()-1) code.append(parType+" "+parName+", ");
						else code.append(parType+" "+parName);
					}
				}
				if(k!=methodelist.size()-1) code.append("){\n\t//function body\n\t}\n");
				else code.append("){\n\t//function body\n\t}\n}\n");
			}
			System.out.println(code);
			
			
			try {
				String repl2 = repl.replace(".xml", "");
			    File folder = new File("src\\org\\jetuml\\aGenerateJava\\Diagram_"+repl2);
			    boolean success = folder.mkdir();
	            BufferedWriter writer = new BufferedWriter(new FileWriter(folder+"\\"+currentClasse.getAttribute("name").getValue()+".java"));
	            writer.write(code.toString()); // Write the StringBuilder contents to the file
	            writer.close();
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
			}
		System.out.println(SQL);
		String Newrep = title.replace(".class.jet", "");
		File folder = new File("src\\org\\jetuml\\aGenerateJava\\Diagram_"+Newrep);
		BufferedWriter writerSQL = new BufferedWriter(new FileWriter(folder+"\\"+Newrep+".sql"));
        writerSQL.write(SQL.toString()); // Write the StringBuilder contents to the file
        writerSQL.close();
	}
	
	public class ClassesRelation {
		private String RelationType; //agregation/composition...
		private String RelationName; //Learn, Read, Teach...
		private String DepartClasse;
		private String ArraivalClasse;
		private String ArraivalClasseMultiplicity;
		
		public ClassesRelation(String RelationType,String RelationName,String DepartClasse,String ArraivalClasse,String ArraivalClasseMultiplicity) {
			this.RelationType = RelationType;
			this.RelationName = RelationName;
			this.DepartClasse = DepartClasse;
			this.ArraivalClasse = ArraivalClasse;
			this.ArraivalClasseMultiplicity = ArraivalClasseMultiplicity;
		}

		public String getRelationType() {
			return RelationType;
		}
		
		public void setRelationType(String relationType) {
			RelationType = relationType;
		}
		
		public String getRelationName() {
			return RelationName;
		}
		
		public void setRelationName(String relationName) {
			RelationName = relationName;
		}
		
		public String getDepartClasse() {
			return DepartClasse;
		}
		
		public void setDepartClasse(String departClasse) {
			DepartClasse = departClasse;
		}
		
		public String getArraivalClasse() {
			return ArraivalClasse;
		}
		
		public void setArraivalClasse(String arraivalClasse) {
			ArraivalClasse = arraivalClasse;
		}
		
		public String getArraivalClasseMultiplicity() {
			return ArraivalClasseMultiplicity;
		}
		
		public void setArraivalClasseMultiplicity(String arraivalClasseMultiplicity) {
			ArraivalClasseMultiplicity = arraivalClasseMultiplicity;
		}
		
		public boolean exists(List<ClassesRelation> mylist,String classe) {
			for(ClassesRelation relation : mylist) {
				if(relation.getDepartClasse().equals(classe)) return true;
			}
			return false;
		}
	}
	
	private FileChooser getImageFileChooser(File pInitialDirectory, String pInitialFormat) 
	{
		assert pInitialDirectory.exists() && pInitialDirectory.isDirectory();
		DiagramTab frame = getSelectedDiagramTab();

		FileChooser fileChooser = new FileChooser();
		for(String format : IMAGE_FORMATS ) 
		{
			ExtensionFilter filter = 
					new ExtensionFilter(format.toUpperCase() + " " + RESOURCES.getString("files.image.name"), "*." +format);
			fileChooser.getExtensionFilters().add(filter);
			if( format.equals(pInitialFormat ))
			{
				fileChooser.setSelectedExtensionFilter(filter);
			}
		}
		fileChooser.setInitialDirectory(pInitialDirectory);

		// If the file was previously saved, use that to suggest a file name root.
		if(frame.getFile().isPresent()) 
		{
			File file = FileExtensions.clipApplicationExtension(frame.getFile().get());
			fileChooser.setInitialDirectory(file.getParentFile());
			fileChooser.setInitialFileName(file.getName());
		}
		return fileChooser;
	}

	private BufferedImage getBufferedImage(DiagramTab pDiagramTab) 
	{
		return SwingFXUtils.fromFXImage(pDiagramTab.createImage(), null);
	}
	
	private int getNumberOfUsavedDiagrams()
	{
		return (int) tabs().stream()
			.filter( tab -> tab instanceof DiagramTab ) 
			.filter( frame -> ((DiagramTab) frame).hasUnsavedChanges())
			.count();
	}

	/**
	 * Exits the program if no graphs have been modified or if the user agrees to
	 * abandon modified graphs.
	 */
	public void exit() 
	{
		final int modcount = getNumberOfUsavedDiagrams();
		if (modcount > 0) 
		{
			Alert alert = new Alert(AlertType.CONFIRMATION, 
					MessageFormat.format(RESOURCES.getString("dialog.exit.ok"), new Object[] { Integer.valueOf(modcount) }),
					ButtonType.YES, 
					ButtonType.NO);
			alert.initOwner(aMainStage);
			alert.setTitle(RESOURCES.getString("dialog.exit.title"));
			alert.setHeaderText(RESOURCES.getString("dialog.exit.title"));
			alert.showAndWait();

			if (alert.getResult() == ButtonType.YES) 
			{
				Preferences.userNodeForPackage(JetUML.class).put("recent", aRecentFiles.serialize());
				System.exit(0);
			}
		}
		else 
		{
			Preferences.userNodeForPackage(JetUML.class).put("recent", aRecentFiles.serialize());
			System.exit(0);
		}
	}		
	
	private List<Tab> tabs()
	{
		return ((TabPane) getCenter()).getTabs();
	}
	
	private TabPane tabPane()
	{
		return (TabPane) getCenter();
	}
	
	private boolean isWelcomeTabShowing()
	{
		return aWelcomeTab != null && 
				tabs().size() == 1 && 
				tabs().get(0) instanceof WelcomeTab;
	}
	
	/* Insert a graph frame into the tabbedpane */ 
	private void insertGraphFrameIntoTabbedPane(DiagramTab pGraphFrame) 
	{
		if( isWelcomeTabShowing() )
		{
			tabs().remove(0);
		}
		tabs().add(pGraphFrame);
		tabPane().getSelectionModel().selectLast();
	}
	
	/*
	 * Shows the welcome tab if there are no other tabs.
	 */
	private void showWelcomeTabIfNecessary() 
	{
		if( tabs().size() == 0)
		{
			aWelcomeTab.loadRecentFileLinks(getOpenFileHandlers());
			tabs().add(aWelcomeTab);
		}
	}
	
	/*
	 * Removes the graph frame from the tabbed pane
	 */
	private void removeGraphFrameFromTabbedPane(DiagramTab pTab) 
	{
		pTab.close();
		tabs().remove(pTab);
		showWelcomeTabIfNecessary();
	}
	
	
}
