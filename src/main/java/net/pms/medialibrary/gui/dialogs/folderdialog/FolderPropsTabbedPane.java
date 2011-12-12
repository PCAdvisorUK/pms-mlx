package net.pms.medialibrary.gui.dialogs.folderdialog;

import java.util.List;
import javax.swing.JTabbedPane;

import net.pms.Messages;
import net.pms.medialibrary.commons.dataobjects.DOFilter;
import net.pms.medialibrary.commons.dataobjects.DOMediaLibraryFolder;
import net.pms.medialibrary.commons.dataobjects.FileDisplayProperties;
import net.pms.medialibrary.commons.enumarations.ConditionType;
import net.pms.medialibrary.commons.enumarations.FileType;
import net.pms.medialibrary.commons.events.FolderDialogActionListener;
import net.pms.medialibrary.commons.exceptions.ConditionException;
import net.pms.medialibrary.commons.exceptions.TemplateException;
import net.pms.medialibrary.commons.interfaces.IMediaLibraryStorage;
import net.pms.medialibrary.storage.MediaLibraryStorage;

public class FolderPropsTabbedPane extends JTabbedPane {
	private static final long    serialVersionUID       = -6327372841599772080L;

	private DOMediaLibraryFolder folder;
	private IMediaLibraryStorage storage;

	private ConditionPanel conditionsPanel;
	private DisplayPanel displayPanel;

	public FolderPropsTabbedPane(DOMediaLibraryFolder folder, List<FolderDialogActionListener> folderDialogActionListeners) {
		this.folder = folder;

		storage = MediaLibraryStorage.getInstance();
		conditionsPanel = new ConditionPanel(folder);
		displayPanel = new DisplayPanel(folder, storage, folderDialogActionListeners);
		
		updateInheritance();

		addTab(Messages.getString("ML.FolderPropsTabbedPane.pDisplay"), displayPanel);
		addTab(Messages.getString("ML.FolderPropsTabbedPane.pConditions"), conditionsPanel);
	}

	private void canInheritConditions(boolean inherit) {
		conditionsPanel.setCanInheritConditions(inherit);
	}

	private void canInheritSortOrder(boolean inherit) {
		displayPanel.canInheritSortOrder(inherit);
	}

	private void canInheritDisplayAs(boolean inherit) {
		displayPanel.canInheritDisplayAs(inherit);
	}

	private void updateInheritance() {
		// check if the folder type is the same as the one of its parent
		boolean isValid = false;
		if (this.folder.getParentFolder() != null 
				&& (this.folder.getParentFolder().getFileType() == this.folder.getFileType()
						|| this.folder.getParentFolder().getFileType() == FileType.FILE)) {
			isValid = true;
		}

		canInheritConditions(isValid);
		canInheritSortOrder(isValid);
		canInheritDisplayAs(isValid);
	}

	protected boolean getInheritsConditions() {
		return conditionsPanel.isInheritsConditions();
	}

	protected FileType getFileType() {
		return this.folder.getFileType();
	}

	protected void setFileType(FileType fileType) {
		this.folder.setFileType(fileType);
		conditionsPanel.setFileType(fileType);
		displayPanel.setFileType(fileType);
		updateInheritance();
	}

	public void resetConditions() {
		conditionsPanel.resetConditions();

	}

	protected DOFilter getFilter() throws ConditionException {
		return conditionsPanel.getFilter();
	}

	protected boolean isDisplayItems() {
		return displayPanel.isDisplayItems();
	}

	protected boolean isInheritDisplayFileAs() {
		return displayPanel.isInheritDisplayFileAs();
	}

	protected boolean isInheritSort() {
		return displayPanel.isInheritSort();
	}

	protected FileDisplayProperties getDisplayProperties() throws TemplateException {
		return displayPanel.getDisplayProperties();
	}

	protected boolean getSortAscending() {
		return displayPanel.getSortAscending();
	}

	protected ConditionType getSortType() {
		return displayPanel.getSortType();
	}
	
	protected int getMaxFiles(){
		return displayPanel.getMaxFiles();
	}

	public boolean hasConditions() {
	    return conditionsPanel.hasConditions();
    }
}
