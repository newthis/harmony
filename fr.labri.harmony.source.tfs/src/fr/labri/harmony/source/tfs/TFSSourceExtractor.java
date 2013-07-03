package fr.labri.harmony.source.tfs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.Change;
import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.ChangeType;
import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.Changeset;
import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.ItemType;
import com.microsoft.tfs.core.clients.workitem.WorkItem;

import fr.labri.harmony.core.config.model.SourceConfiguration;
import fr.labri.harmony.core.dao.Dao;
import fr.labri.harmony.core.model.Action;
import fr.labri.harmony.core.model.ActionKind;
import fr.labri.harmony.core.model.Author;
import fr.labri.harmony.core.model.Event;
import fr.labri.harmony.core.model.Item;
import fr.labri.harmony.core.source.AbstractSourceExtractor;





public class TFSSourceExtractor extends AbstractSourceExtractor<TFSWorkspace> {

	private static final String COMMIT_LOG = "commit_log";

	
	public TFSSourceExtractor() {
		super();
	}

	public TFSSourceExtractor(SourceConfiguration config, Dao dao, Properties properties) {
		super(config, dao, properties);
	}
	
	@Override
	public void initializeWorkspace() {
		workspace = new TFSWorkspace(this);
		workspace.init();

	}

	@Override
	public void extractEvents() {
		Changeset[] changesets = workspace.getChangeset();

		Event[] events = new Event[changesets.length];

		for (int i = 0; i < changesets.length; ++i) {
			Changeset changeset = changesets[i];
			
			int eventId = changeset.getChangesetID();
			long eventTime = changeset.getDate().getTimeInMillis() / 10;	
			
			
			// Author Identification
			String userName = changeset.getOwner();
			String displayName = changeset.getOwnerDisplayName();			
			Author author = getAuthor(userName);
            if (author == null) {
                    author = new Author(source,userName, displayName);
                    saveAuthor(author);
            }
            List<Author> authors = new ArrayList<>(Arrays.asList(new Author[] { author }));

            // Parent identification
            // TODO check this definition of parent        
            List<Event> parents = new ArrayList<>();
			if (i != 0) parents.add(events[i - 1]);

			Event e = new Event(source, String.valueOf(eventId), eventTime, parents, authors);
			saveEvent(e);
			
			events[i] = e;

			// TODO Add management metadata
			/*Metadata metadata = new Metadata();
			metadata.getMetadata().put(COMMIT_LOG, changeset.getComment());
			metadata.getMetadata().put("committer", changeset.getCommitter());
			metadata.getMetadata().put("committer-display-name", changeset.getCommitterDisplayName());
			e.getData().add(metadata);
			metadata.setHarmonyElement(e);*/
			
			// TODO Requirements
			//WorkItem wi[] = changeset.getWorkItems();

	
		}

	}

	@Override
	public void extractActions(Event e) {

			Changeset changeset = workspace.getTFSClient().getChangeset(Integer.parseInt(e.getNativeId()));
			Change[] changes = changeset.getChanges();
			for (Change change : changes) {
				
	
				// We do not track folders
				if (change.getItem().getItemType().equals(ItemType.FILE)) {
					String itemId = Integer.toString(change.getItem().getItemID());
					
					 Item i = getItem(itemId);
                     if (i == null) {
                             i = new Item(source, itemId);
                             saveItem(i);
                     }
	
					ActionKind kind = null;
					ChangeType changeType = change.getChangeType();
					if (changeType.contains(ChangeType.ADD)) kind = ActionKind.Create;
					else if (changeType.contains(ChangeType.EDIT)) kind = ActionKind.Edit;
					else if (changeType.contains(ChangeType.DELETE)) kind = ActionKind.Delete;
					
					//We check if the related event has parents, if it the case we select arbitrarily the first one as parent of the action.
					Event parentOfA = null;
					if(e.getParents().isEmpty()){
						parentOfA = e;
					} else{
						parentOfA= e.getParents().get(0);
					}
					Action a = new Action(i, kind, e, parentOfA, source);
                    saveAction(a);
					
					// TODO Add metadata management
					/*String serverPath = change.getItem().getServerItem();
					Metadata metadata = new Metadata();
					metadata.getMetadata().put("server-path", serverPath);

					i.getData().add(metadata);
					metadata.setHarmonyElement(i);*/
				}
			}
	}

}
