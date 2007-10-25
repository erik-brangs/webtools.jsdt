/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.wst.jsdt.internal.ui.packageview;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.IBasicPropertyConstants;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.wst.jsdt.core.ElementChangedEvent;
import org.eclipse.wst.jsdt.core.IClassFile;
import org.eclipse.wst.jsdt.core.IClasspathEntry;
import org.eclipse.wst.jsdt.core.ICompilationUnit;
import org.eclipse.wst.jsdt.core.IElementChangedListener;
import org.eclipse.wst.jsdt.core.IJavaElement;
import org.eclipse.wst.jsdt.core.IJavaElementDelta;
import org.eclipse.wst.jsdt.core.IJavaModel;
import org.eclipse.wst.jsdt.core.IJavaProject;
import org.eclipse.wst.jsdt.core.IPackageFragment;
import org.eclipse.wst.jsdt.core.IPackageFragmentRoot;
import org.eclipse.wst.jsdt.core.JavaCore;
import org.eclipse.wst.jsdt.core.JavaModelException;
import org.eclipse.wst.jsdt.internal.corext.util.JavaModelUtil;
import org.eclipse.wst.jsdt.internal.ui.JavaPlugin;
import org.eclipse.wst.jsdt.internal.ui.navigator.ContainerFolder;
import org.eclipse.wst.jsdt.internal.ui.workingsets.WorkingSetModel;
import org.eclipse.wst.jsdt.ui.PreferenceConstants;
import org.eclipse.wst.jsdt.ui.StandardJavaElementContentProvider;
 
/**
 * Content provider for the PackageExplorer.
 * 
 * <p>
 * Since 2.1 this content provider can provide the children for flat or hierarchical
 * layout.
 * </p>
 * 
 * @see org.eclipse.wst.jsdt.ui.StandardJavaElementContentProvider
 */
public class PackageExplorerContentProvider extends StandardJavaElementContentProvider implements ITreeContentProvider, IElementChangedListener, IPropertyChangeListener {
	
	protected static final int ORIGINAL= 0;
	protected static final int PARENT= 1 << 0;
	protected static final int GRANT_PARENT= 1 << 1;
	protected static final int PROJECT= 1 << 2;
	
	private TreeViewer fViewer;
	private Object fInput;
	private boolean fIsFlatLayout;
	private boolean fShowLibrariesNode;
	private boolean fFoldPackages;
	
	private Collection fPendingUpdates;
		
	/**
	 * Creates a new content provider for Java elements.
	 * @param provideMembers if set, members of compilation units and class files are shown
	 */
	public PackageExplorerContentProvider(boolean provideMembers) {
		super(provideMembers);
		fShowLibrariesNode= false;
		fIsFlatLayout= false;
		fFoldPackages= arePackagesFoldedInHierarchicalLayout();
		fPendingUpdates= null;
		JavaPlugin.getDefault().getPreferenceStore().addPropertyChangeListener(this);
	}
	
	private boolean arePackagesFoldedInHierarchicalLayout(){
		return PreferenceConstants.getPreferenceStore().getBoolean(PreferenceConstants.APPEARANCE_FOLD_PACKAGES_IN_PACKAGE_EXPLORER);
	}
			
	protected Object getViewerInput() {
		return fInput;
	}
	
	/* (non-Javadoc)
	 * Method declared on IElementChangedListener.
	 */
	public void elementChanged(final ElementChangedEvent event) {
		final ArrayList runnables= new ArrayList();
		try {
			// 58952 delete project does not update Package Explorer [package explorer] 
			// if the input to the viewer is deleted then refresh to avoid the display of stale elements
			if (inputDeleted(runnables))
				return;

			processDelta(event.getDelta(), runnables);
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
		} finally {	
			executeRunnables(runnables);
		}
	}

	protected final void executeRunnables(final Collection runnables) {

		// now post all collected runnables
		Control ctrl= fViewer.getControl();
		if (ctrl != null && !ctrl.isDisposed()) {
			//Are we in the UIThread? If so spin it until we are done
			if (ctrl.getDisplay().getThread() == Thread.currentThread()) {
				runUpdates(runnables);
			} else {
				synchronized (this) {
					if (fPendingUpdates == null) {
						fPendingUpdates= runnables;
					} else {
						fPendingUpdates.addAll(runnables);
					}
				}
				ctrl.getDisplay().asyncExec(new Runnable(){
					public void run() {
						runPendingUpdates();
					}
				});
			}
		}
	}
	
	/**
	 * Run all of the runnables that are the widget updates. Must be called in the display thread.
	 */
	public void runPendingUpdates() {
		Collection pendingUpdates;
		synchronized (this) {
			pendingUpdates= fPendingUpdates;
			fPendingUpdates= null;
		}
		if (pendingUpdates != null && fViewer != null) {
			Control control = fViewer.getControl();
			if (control != null && !control.isDisposed()) {
				runUpdates(pendingUpdates);
			}
		}
	}
	
	private void runUpdates(Collection runnables) {
		Iterator runnableIterator = runnables.iterator();
		while (runnableIterator.hasNext()){
			((Runnable) runnableIterator.next()).run();
		}
	}
	

	private boolean inputDeleted(Collection runnables) {
		if (fInput == null)
			return false;
		if ((fInput instanceof IJavaElement) && ((IJavaElement) fInput).exists())
			return false;
		if ((fInput instanceof IResource) && ((IResource) fInput).exists())
			return false;
		if (fInput instanceof WorkingSetModel)
			return false;
		if (fInput instanceof IWorkingSet) // see https://bugs.eclipse.org/bugs/show_bug.cgi?id=156239
			return false;
		postRefresh(fInput, ORIGINAL, fInput, runnables);
		return true;
	}

	/* (non-Javadoc)
	 * Method declared on IContentProvider.
	 */
	public void dispose() {
		super.dispose();
		JavaCore.removeElementChangedListener(this);
		JavaPlugin.getDefault().getPreferenceStore().removePropertyChangeListener(this);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.wst.jsdt.ui.StandardJavaElementContentProvider#getPackageFragmentRootContent(org.eclipse.wst.jsdt.core.IPackageFragmentRoot)
	 */
	protected Object[] getPackageFragmentRootContent(IPackageFragmentRoot root) throws JavaModelException {
		if (fIsFlatLayout) {
			return super.getPackageFragmentRootContent(root);
		}
		
		// hierarchical package mode
		ArrayList result= new ArrayList();
		getHierarchicalPackageChildren(root, null, result);
		if (!isProjectPackageFragmentRoot(root)) {
			Object[] nonJavaResources= root.getNonJavaResources();
			for (int i= 0; i < nonJavaResources.length; i++) {
				result.add(nonJavaResources[i]);
			}
		}
		return result.toArray();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.wst.jsdt.ui.StandardJavaElementContentProvider#getPackageContent(org.eclipse.wst.jsdt.core.IPackageFragment)
	 */
	protected Object[] getPackageContent(IPackageFragment fragment) throws JavaModelException {
		if (fIsFlatLayout) {
			return super.getPackageContent(fragment);
		}
		
		// hierarchical package mode
		ArrayList result= new ArrayList();
		
		getHierarchicalPackageChildren((IPackageFragmentRoot) fragment.getParent(), fragment, result);
		Object[] nonPackages= super.getPackageContent(fragment);
		if (result.isEmpty())
			return nonPackages;
		for (int i= 0; i < nonPackages.length; i++) {
			result.add(nonPackages[i]);
		}
		return result.toArray();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.wst.jsdt.ui.StandardJavaElementContentProvider#getFolderContent(org.eclipse.core.resources.IFolder)
	 */
	protected Object[] getFolderContent(IFolder folder) throws CoreException {
		if (fIsFlatLayout) {
			return super.getFolderContent(folder);
		}
		
		// hierarchical package mode
		ArrayList result= new ArrayList();
		
		getHierarchicalPackagesInFolder(folder, result);
		Object[] others= super.getFolderContent(folder);
		if (result.isEmpty())
			return others;
		for (int i= 0; i < others.length; i++) {
			result.add(others[i]);
		}
		return result.toArray();
	}
	
	
	public Object[] getChildren(Object parentElement) {
		try {
			if (parentElement instanceof IJavaModel) 
				return concatenate(getJavaProjects((IJavaModel)parentElement), getNonJavaProjects((IJavaModel)parentElement));

			if(parentElement instanceof ContainerFolder) {
				return getContainerPackageFragmentRoots((PackageFragmentRootContainer)((ContainerFolder)parentElement).getParentObject());
			}
			if (parentElement instanceof PackageFragmentRootContainer)
				return getContainerPackageFragmentRoots((PackageFragmentRootContainer)parentElement, true);
				
			if (parentElement instanceof IProject) 
				return ((IProject)parentElement).members();
			
			if(parentElement instanceof IPackageFragmentRoot && ((IPackageFragmentRoot)parentElement).isVirtual()) {
				return getLibraryChildren((IPackageFragmentRoot)parentElement);
			}
			
			return super.getChildren(parentElement);
		} catch (CoreException e) {
			return NO_CHILDREN;
		}
	}
private Object[] getLibraryChildren(IPackageFragmentRoot container) {
		
		
		Object[] children=null;
		try {
			children = container.getChildren();
		} catch (JavaModelException ex1) {
			// TODO Auto-generated catch block
			ex1.printStackTrace();
		}
		if(children==null) return null;
		ArrayList allChildren = new ArrayList();
		
		boolean unique = false;
		try {
			while(!unique && children!=null && children.length>0) {
				for(int i = 0;i<children.length;i++) {
					String display1 = ((IJavaElement)children[0]).getDisplayName();
					String display2 = ((IJavaElement)children[i]).getDisplayName();
					if(!(   (display1==display2) || (display1!=null && display1.compareTo(display2)==0))){
						allChildren.addAll(Arrays.asList(children));
						unique=true;
						break;
					}
				}
				ArrayList more = new ArrayList();
				for(int i = 0;!unique && i<children.length;i++) {
					if(children[i] instanceof IPackageFragment) {
						more.addAll(Arrays.asList(((IPackageFragment)children[i]).getChildren()));
					}else if(children[i] instanceof IPackageFragmentRoot) {
						more.addAll(Arrays.asList(((IPackageFragmentRoot)children[i]).getChildren()));
					}else if(children[i] instanceof IClassFile) {
						more.addAll(Arrays.asList( filter(((IClassFile)children[i]).getChildren())) );
					}else if(children[i] instanceof ICompilationUnit) {
						more.addAll(Arrays.asList( filter(((ICompilationUnit)children[i]).getChildren())) );
					}else {
						/* bottomed out, now at javaElement level */
						unique=true;
						break;
					}
					
				}
				if(!unique) children = more.toArray();
			}
		} catch (JavaModelException ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
		}
		
		
		return allChildren.toArray();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.wst.jsdt.ui.StandardJavaElementContentProvider#getPackageFragmentRoots(org.eclipse.wst.jsdt.core.IJavaProject)
	 */
	protected Object[] getPackageFragmentRoots(IJavaProject project) throws JavaModelException {
		if (!project.getProject().isOpen())
			return NO_CHILDREN;
			
		List result= new ArrayList();

		boolean addJARContainer= false;
		
		IPackageFragmentRoot[] roots= project.getPackageFragmentRoots();
		for (int i= 0; i < roots.length; i++) {
			IPackageFragmentRoot root= roots[i];
			IClasspathEntry classpathEntry= root.getRawClasspathEntry();
			int entryKind= classpathEntry.getEntryKind();
			if (entryKind == IClasspathEntry.CPE_CONTAINER) {
				// all ClassPathContainers are added later 
			} else if (fShowLibrariesNode && (entryKind == IClasspathEntry.CPE_LIBRARY || entryKind == IClasspathEntry.CPE_VARIABLE)) {
				addJARContainer= true;
			} else {
				if (isProjectPackageFragmentRoot(root)) {
					// filter out package fragments that correspond to projects and
					// replace them with the package fragments directly
					Object[] fragments= getPackageFragmentRootContent(root);
					for (int j= 0; j < fragments.length; j++) {
						result.add(fragments[j]);
					}
				} else {
					result.add(root);
				}
			}
		}
		
		if (addJARContainer) {
			result.add(new LibraryContainer(project));
		}
		
		// separate loop to make sure all containers are on the classpath
		IClasspathEntry[] rawClasspath= project.getRawClasspath();
		for (int i= 0; i < rawClasspath.length; i++) {
			IClasspathEntry classpathEntry= rawClasspath[i];
			if (classpathEntry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
				result.add(new ClassPathContainer(project, classpathEntry));
			}	
		}	
		Object[] resources= project.getNonJavaResources();
		for (int i= 0; i < resources.length; i++) {
			result.add(resources[i]);
		}
		return result.toArray();
	}
	private Object[] getContainerPackageFragmentRoots3(PackageFragmentRootContainer container) {
		Object[] children = container.getChildren();
		if(children==null) return null;
		ArrayList allChildren = new ArrayList();
		for(int i=0;i<children.length;i++) {
			try {
				allChildren.addAll(Arrays.asList(((IPackageFragmentRoot)children[i]).getChildren()));
			} catch (JavaModelException ex) {
				
			}
		}
		return allChildren.toArray();
	}
	
	private Object[] getContainerPackageFragmentRoots(PackageFragmentRootContainer container) {
		return getContainerPackageFragmentRoots(container, false);
	}
	
	private Object[] getContainerPackageFragmentRoots(PackageFragmentRootContainer container, boolean createFolder) {
		
		
		if(container!=null) {	
			
			Object[] children = container.getChildren();
			if(children==null) return null;
			ArrayList allChildren = new ArrayList();
			
			boolean unique = false;
			
				while(!unique && children!=null && children.length>0) {
					String display1=null;
					for(int i = 0;i<children.length;i++) {
						display1 = ((IJavaElement)children[0]).getDisplayName();
						String display2 = ((IJavaElement)children[i]).getDisplayName();
						if(!(   (display1==display2) || (display1!=null && display1.compareTo(display2)==0))){
							allChildren.addAll(Arrays.asList(children));
							unique=true;
							break;
						}
					}
					if(!unique && createFolder) {
						ContainerFolder folder = new ContainerFolder(display1, container);
						return new Object[] {folder};
					}
					ArrayList more = new ArrayList();
					for(int i = 0;!unique && i<children.length;i++) {
					
						try {
							if(children[i] instanceof IPackageFragment) {
								more.addAll(Arrays.asList(((IPackageFragment)children[i]).getChildren()));
							}else if(children[i] instanceof IPackageFragmentRoot) {
								more.addAll(Arrays.asList(((IPackageFragmentRoot)children[i]).getChildren()));
							}else if(children[i] instanceof IClassFile) {
								more.addAll(Arrays.asList( filter(((IClassFile)children[i]).getChildren())) );
							}else if(children[i] instanceof ICompilationUnit) {
								more.addAll(Arrays.asList( filter(((ICompilationUnit)children[i]).getChildren())) );
							}else {
								/* bottomed out, now at javaElement level */
								unique=true;
								break;
							}
						} catch (JavaModelException ex) {
							// TODO Auto-generated catch block
							ex.printStackTrace();
						}
						
					}
					if(!unique) children = more.toArray();
				}
		
			
			
			return allChildren.toArray();
		}else {
		
			
			Object[] children = container.getChildren();
			if(children==null) return null;
			ArrayList allChildren = new ArrayList();
			for(int i=0;i<children.length;i++) {
				try {
					allChildren.addAll(Arrays.asList(((IPackageFragmentRoot)children[i]).getChildren()));
				} catch (JavaModelException ex) {
					
				}
			}
			return allChildren.toArray();
		}
	}

	private Object[] getNonJavaProjects(IJavaModel model) throws JavaModelException {
		return model.getNonJavaResources();
	}

	protected Object internalGetParent(Object element) {
		if (!fIsFlatLayout && element instanceof IPackageFragment) {
			return getHierarchicalPackageParent((IPackageFragment) element);
		} else if (element instanceof IPackageFragmentRoot) {
			// since we insert logical package containers we have to fix
			// up the parent for package fragment roots so that they refer
			// to the container and containers refer to the project
			IPackageFragmentRoot root= (IPackageFragmentRoot)element;
			
			try {
				IClasspathEntry entry= root.getRawClasspathEntry();
				int entryKind= entry.getEntryKind();
				if (entryKind == IClasspathEntry.CPE_CONTAINER) {
					return new ClassPathContainer(root.getJavaProject(), entry);
				} else if (fShowLibrariesNode && (entryKind == IClasspathEntry.CPE_LIBRARY || entryKind == IClasspathEntry.CPE_VARIABLE)) {
					return new LibraryContainer(root.getJavaProject());
				}
			} catch (JavaModelException e) {
				// fall through
			}
		} else if (element instanceof PackageFragmentRootContainer) {
			return ((PackageFragmentRootContainer)element).getJavaProject();
		}
		return super.internalGetParent(element);
	}

	/* (non-Javadoc)
	 * Method declared on IContentProvider.
	 */
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		super.inputChanged(viewer, oldInput, newInput);
		fViewer= (TreeViewer)viewer;
		if (oldInput == null && newInput != null) {
			JavaCore.addElementChangedListener(this); 
		} else if (oldInput != null && newInput == null) {
			JavaCore.removeElementChangedListener(this); 
		}
		fInput= newInput;
	}

	// hierarchical packages
	/**
	 * Returns the hierarchical packages inside a given fragment or root.
	 * @param parent The parent package fragment root
	 * @param fragment The package to get the children for or 'null' to get the children of the root.
	 * @param result Collection where the resulting elements are added
	 * @throws JavaModelException
	 */
	private void getHierarchicalPackageChildren(IPackageFragmentRoot parent, IPackageFragment fragment, Collection result) throws JavaModelException {
		IJavaElement[] children= parent.getChildren();
		String prefix= fragment != null ? fragment.getElementName() + '/' : ""; //$NON-NLS-1$
		if (prefix.length()==1)
			prefix="";
		int prefixLen= prefix.length();
		for (int i= 0; i < children.length; i++) {
			IPackageFragment curr= (IPackageFragment) children[i];
			if (fragment==null)
			{
				if (curr.isDefaultPackage()) 
					result.add(curr);
			}
			else
			{
				String name= curr.getElementName();
				if (name.startsWith(prefix) && name.length() > prefixLen && name.indexOf('/', prefixLen) == -1) {
					if (fFoldPackages) {
						curr= getFolded(children, curr);
					}
					result.add(curr);
				}
			}

		}
	}
	
	/**
	 * Returns the hierarchical packages inside a given folder.
	 * @param folder The parent folder
	 * @param result Collection where the resulting elements are added
	 * @throws CoreException thrown when elements could not be accessed
	 */
	private void getHierarchicalPackagesInFolder(IFolder folder, Collection result) throws CoreException {
		IResource[] resources= folder.members();
		for (int i= 0; i < resources.length; i++) {
			IResource resource= resources[i];
			if (resource instanceof IFolder) {
				IFolder curr= (IFolder) resource;
				IJavaElement element= JavaCore.create(curr);
				if (element instanceof IPackageFragment) {
					if (fFoldPackages) {
						IPackageFragment fragment= (IPackageFragment) element;
						IPackageFragmentRoot root= (IPackageFragmentRoot) fragment.getParent();
						element= getFolded(root.getChildren(), fragment);
					}
					result.add(element);	
				} 
			}	
		}
	}

	public Object getHierarchicalPackageParent(IPackageFragment child) {
		String name= child.getElementName();
		IPackageFragmentRoot parent= (IPackageFragmentRoot) child.getParent();
		int index= name.lastIndexOf('/');
		if (index != -1) {
			String realParentName= name.substring(0, index);
			IPackageFragment element= parent.getPackageFragment(realParentName);
			if (element.exists()) {
				try {
					if (fFoldPackages && isEmpty(element) && findSinglePackageChild(element, parent.getChildren()) != null) {
						return getHierarchicalPackageParent(element);
					}
				} catch (JavaModelException e) {
					// ignore
				}
				return element;
			} else { // bug 65240
				IResource resource= element.getResource();
				if (resource != null) {
					return resource;
				}
			}
		}
		if (parent.getResource() instanceof IProject) {
			return parent.getJavaProject();
		}
		return parent;
	}
	
	private static IPackageFragment getFolded(IJavaElement[] children, IPackageFragment pack) throws JavaModelException {
		while (isEmpty(pack)) {
			IPackageFragment collapsed= findSinglePackageChild(pack, children);
			if (collapsed == null) {
				return pack;
			}
			pack= collapsed;
		}
		return pack;
	}
		
	private static boolean isEmpty(IPackageFragment fragment) throws JavaModelException {
		return !fragment.containsJavaResources() && fragment.getNonJavaResources().length == 0;
	}
	
	private static IPackageFragment findSinglePackageChild(IPackageFragment fragment, IJavaElement[] children) {
		String prefix= fragment.getElementName() + '/';
		int prefixLen= prefix.length();
		IPackageFragment found= null;
		for (int i= 0; i < children.length; i++) {
			IJavaElement element= children[i];
			String name= element.getElementName();
			if (name.startsWith(prefix) && name.length() > prefixLen && name.indexOf('/', prefixLen) == -1) {
				if (found == null) {
					found= (IPackageFragment) element;
				} else {
					return null;
				}
			}
		}
		return found;
	}
	
	// ------ delta processing ------

	/**
	 * Processes a delta recursively. When more than two children are affected the
	 * tree is fully refreshed starting at this node.
	 * 
	 * @param delta the delta to process
	 * @param runnables the resulting view changes as runnables (type {@link Runnable})
	 * @return true is returned if the conclusion is to refresh a parent of an element. In that case no siblings need
	 * to be processed
	 * @throws JavaModelException thrown when the access to an element failed
	 */
	private boolean processDelta(IJavaElementDelta delta, Collection runnables) throws JavaModelException {
	
		int kind= delta.getKind();
		int flags= delta.getFlags();
		IJavaElement element= delta.getElement();
		int elementType= element.getElementType();
		
		
		if (elementType != IJavaElement.JAVA_MODEL && elementType != IJavaElement.JAVA_PROJECT) {
			IJavaProject proj= element.getJavaProject();
			if (proj == null || !proj.getProject().isOpen()) // TODO: Not needed if parent already did the 'open' check!
				return false;	
		}
		
		if (!fIsFlatLayout && elementType == IJavaElement.PACKAGE_FRAGMENT) {
			if (kind == IJavaElementDelta.REMOVED) {
				final Object parent = getHierarchicalPackageParent((IPackageFragment) element);
				if (parent instanceof IPackageFragmentRoot) {
					postRemove(element,  runnables);
					return false;
				} else {
					postRefresh(internalGetParent(parent), GRANT_PARENT, element, runnables);
					return true;
				}
			} else if (kind == IJavaElementDelta.ADDED) {
				final Object parent = getHierarchicalPackageParent((IPackageFragment) element);
				if (parent instanceof IPackageFragmentRoot) {
					postAdd(parent, element,  runnables);
					return false;
				} else {
					postRefresh(internalGetParent(parent), GRANT_PARENT, element, runnables);
					return true;
				}
			}
			handleAffectedChildren(delta, element, runnables);
			return false;
		}
		
		if (elementType == IJavaElement.COMPILATION_UNIT) {
			ICompilationUnit cu= (ICompilationUnit) element;
			if (!JavaModelUtil.isPrimary(cu)) {
				return false;
			}
						
			if (!getProvideMembers() && cu.isWorkingCopy() && kind == IJavaElementDelta.CHANGED) {
				return false;
			}
			
			if ((kind == IJavaElementDelta.CHANGED) && !isStructuralCUChange(flags)) {
				return false; // test moved ahead
			}
			
			if (!isOnClassPath(cu)) { // TODO: isOnClassPath expensive! Should be put after all cheap tests
				return false;
			}
			
		}
		
		if (elementType == IJavaElement.JAVA_PROJECT) {
			// handle open and closing of a project
			if ((flags & (IJavaElementDelta.F_CLOSED | IJavaElementDelta.F_OPENED)) != 0) {			
				postRefresh(element, ORIGINAL, element, runnables);
				return false;
			}
			// if the raw class path has changed we refresh the entire project
			if ((flags & IJavaElementDelta.F_CLASSPATH_CHANGED) != 0) {
				postRefresh(element, ORIGINAL, element, runnables);
				return false;				
			}
			// if added it could be that the corresponding IProject is already shown. Remove it first.
			// bug 184296
			if (kind == IJavaElementDelta.ADDED) { 
				postRemove(element.getResource(), runnables);
				postAdd(element.getParent(), element, runnables);
				return false;
			}
		}
	
		if (kind == IJavaElementDelta.REMOVED) {
			Object parent= internalGetParent(element);			
			if (element instanceof IPackageFragment) {
				// refresh package fragment root to allow filtering empty (parent) packages: bug 72923
				if (fViewer.testFindItem(parent) != null)
					postRefresh(parent, PARENT, element, runnables);
				return true;
			}
			
			postRemove(element, runnables);
			if (parent instanceof IPackageFragment) 
				postUpdateIcon((IPackageFragment)parent, runnables);
			// we are filtering out empty subpackages, so we
			// a package becomes empty we remove it from the viewer. 
			if (isPackageFragmentEmpty(element.getParent())) {
				if (fViewer.testFindItem(parent) != null)
					postRefresh(internalGetParent(parent), GRANT_PARENT, element, runnables);
				return true;
			}  
			return false;
		}
	
		if (kind == IJavaElementDelta.ADDED) { 
			Object parent= internalGetParent(element);
			// we are filtering out empty subpackages, so we
			// have to handle additions to them specially. 
			if (parent instanceof IPackageFragment) {
				Object grandparent= internalGetParent(parent);
				// 1GE8SI6: ITPJUI:WIN98 - Rename is not shown in Packages View
				// avoid posting a refresh to an invisible parent
				if (parent.equals(fInput)) {
					postRefresh(parent, PARENT, element, runnables);
				} else {
					// refresh from grandparent if parent isn't visible yet
					if (fViewer.testFindItem(parent) == null)
						postRefresh(grandparent, GRANT_PARENT, element, runnables);
					else {
						postRefresh(parent, PARENT, element, runnables);
					}	
				}
				return true;		
			} else {  
				postAdd(parent, element, runnables);
			}
		}
	
		if (elementType == IJavaElement.COMPILATION_UNIT) {
			if (kind == IJavaElementDelta.CHANGED) {
				// isStructuralCUChange already performed above
				postRefresh(element, ORIGINAL, element, runnables);
				updateSelection(delta, runnables);
			}
			return false;
		}
		// no changes possible in class files
		if (elementType == IJavaElement.CLASS_FILE)
			return false;
		
		
		if (elementType == IJavaElement.PACKAGE_FRAGMENT_ROOT) {
			// the contents of an external JAR has changed
			if ((flags & IJavaElementDelta.F_ARCHIVE_CONTENT_CHANGED) != 0) {
				postRefresh(element, ORIGINAL, element, runnables);
				return false;
			}
			// the source attachment of a JAR has changed
			if ((flags & (IJavaElementDelta.F_SOURCEATTACHED | IJavaElementDelta.F_SOURCEDETACHED)) != 0)
				postUpdateIcon(element, runnables);
			
			if (isClassPathChange(delta)) {
				 // throw the towel and do a full refresh of the affected java project. 
				postRefresh(element.getJavaProject(), PROJECT, element, runnables);
				return true;
			}
		}	
		handleAffectedChildren(delta, element, runnables);
		return false;
	}
	
	private static boolean isStructuralCUChange(int flags) {
		// No refresh on working copy creation (F_PRIMARY_WORKING_COPY)
		return ((flags & IJavaElementDelta.F_CHILDREN) != 0) || ((flags & (IJavaElementDelta.F_CONTENT | IJavaElementDelta.F_FINE_GRAINED)) == IJavaElementDelta.F_CONTENT);
	}
	
	/* package */ void handleAffectedChildren(IJavaElementDelta delta, IJavaElement element, Collection runnables) throws JavaModelException {
		int count= 0;
		
		IResourceDelta[] resourceDeltas= delta.getResourceDeltas();
		if (resourceDeltas != null) {
			for (int i= 0; i < resourceDeltas.length; i++) {
				int kind= resourceDeltas[i].getKind();
				if (kind == IResourceDelta.ADDED || kind == IResourceDelta.REMOVED) {
					count++;
				}
			}
		}
		IJavaElementDelta[] affectedChildren= delta.getAffectedChildren();
		for (int i= 0; i < affectedChildren.length; i++) {
			int kind= affectedChildren[i].getKind();
			if (kind == IJavaElementDelta.ADDED || kind == IJavaElementDelta.REMOVED) {
				count++;
			}
		}

		if (count > 1) {
			// more than one child changed, refresh from here downwards
			if (element instanceof IPackageFragment) {
				// a package fragment might become non empty refresh from the parent
				IJavaElement parent= (IJavaElement) internalGetParent(element);
				// 1GE8SI6: ITPJUI:WIN98 - Rename is not shown in Packages View
				// avoid posting a refresh to an invisible parent
				if (element.equals(fInput)) {
					postRefresh(element, ORIGINAL, element, runnables);
				} else {
					postRefresh(parent, PARENT, element, runnables);
				}
			} else if (element instanceof IPackageFragmentRoot) {
				Object toRefresh= internalGetParent(element);
				postRefresh(toRefresh, ORIGINAL, toRefresh, runnables);
			} else {
				postRefresh(element, ORIGINAL, element, runnables);
			}
			return;
		}
		if (resourceDeltas != null) {
			for (int i= 0; i < resourceDeltas.length; i++) {
				if (processResourceDelta(resourceDeltas[i], element, runnables)) {
					return; // early return, element got refreshed
				}
			}
		}
		for (int i= 0; i < affectedChildren.length; i++) {
			if (processDelta(affectedChildren[i], runnables)) {
				return; // early return, element got refreshed
			}
		}
	}
	
	protected void processAffectedChildren(IJavaElementDelta[] affectedChildren, Collection runnables) throws JavaModelException {
		for (int i= 0; i < affectedChildren.length; i++) {
			processDelta(affectedChildren[i], runnables);
		}
	}

	private boolean isOnClassPath(ICompilationUnit element) {
		IJavaProject project= element.getJavaProject();
		if (project == null || !project.exists())
			return false;
		return project.isOnClasspath(element);
	}

	/**
	 * Updates the selection. It finds newly added elements
	 * and selects them.
	 * @param delta the delta to process
	 * @param runnables the resulting view changes as runnables (type {@link Runnable})
	 */
	private void updateSelection(IJavaElementDelta delta, Collection runnables) {
		final IJavaElement addedElement= findAddedElement(delta);
		if (addedElement != null) {
			final StructuredSelection selection= new StructuredSelection(addedElement);
			runnables.add(new Runnable() {
				public void run() {
					// 19431
					// if the item is already visible then select it
					if (fViewer.testFindItem(addedElement) != null)
						fViewer.setSelection(selection);
				}
			});	
		}	
	}

	private IJavaElement findAddedElement(IJavaElementDelta delta) {
		if (delta.getKind() == IJavaElementDelta.ADDED)  
			return delta.getElement();
		
		IJavaElementDelta[] affectedChildren= delta.getAffectedChildren();
		for (int i= 0; i < affectedChildren.length; i++) 
			return findAddedElement(affectedChildren[i]);
			
		return null;
	}

	/**
	 * Updates the package icon
	 * @param element the element to update
	 * @param runnables the resulting view changes as runnables (type {@link Runnable})
	 */
	 private void postUpdateIcon(final IJavaElement element, Collection runnables) {
		 runnables.add(new Runnable() {
			public void run() {
				// 1GF87WR: ITPUI:ALL - SWTEx + NPE closing a workbench window.
				fViewer.update(element, new String[]{IBasicPropertyConstants.P_IMAGE});
			}
		});
	 }

	/**
	 * Process a resource delta.
	 * 
	 * @param delta the delta to process
	 * @param parent the parent
	 * @param runnables the resulting view changes as runnables (type {@link Runnable})
	 * @return true if the parent got refreshed
	 */
	private boolean processResourceDelta(IResourceDelta delta, Object parent, Collection runnables) {
		int status= delta.getKind();
		int flags= delta.getFlags();
		
		IResource resource= delta.getResource();
		// filter out changes affecting the output folder
		if (resource == null)
			return false;	
			
		// this could be optimized by handling all the added children in the parent
		if ((status & IResourceDelta.REMOVED) != 0) {
			if (parent instanceof IPackageFragment) {
				// refresh one level above to deal with empty package filtering properly
				postRefresh(internalGetParent(parent), PARENT, parent, runnables);
				return true;
			} else 
				postRemove(resource, runnables);
		}
		if ((status & IResourceDelta.ADDED) != 0) {
			if (parent instanceof IPackageFragment) {
				// refresh one level above to deal with empty package filtering properly
				postRefresh(internalGetParent(parent), PARENT, parent, runnables);	
				return true;
			} else
				postAdd(parent, resource, runnables);
		}
		// open/close state change of a project
		if ((flags & IResourceDelta.OPEN) != 0) {
			postProjectStateChanged(internalGetParent(parent), runnables);
			return true;		
		}
		IResourceDelta[] resourceDeltas= delta.getAffectedChildren();
		for (int i= 0; i < resourceDeltas.length; i++) {
			if (processResourceDelta(resourceDeltas[i], resource, runnables)) {
				return false; // early return, element got refreshed
			}
		}
		return false;
	}
	
	public void setIsFlatLayout(boolean state) {
		fIsFlatLayout= state;
	}
	
	public void setShowLibrariesNode(boolean state) {
		fShowLibrariesNode= state;
	}
	
	private void postRefresh(Object root, int relation, Object affectedElement, Collection runnables) {
		// JFace doesn't refresh when object isn't part of the viewer
		// Therefore move the refresh start down to the viewer's input
		if (isParent(root, fInput)) 
			root= fInput;
		List toRefresh= new ArrayList(1);
		toRefresh.add(root);
		augmentElementToRefresh(toRefresh, relation, affectedElement);
		postRefresh(toRefresh, true, runnables);
	}
	
	protected void augmentElementToRefresh(List toRefresh, int relation, Object affectedElement) {
	}

	private boolean isParent(Object root, Object child) {
		Object parent= getParent(child);
		if (parent == null)
			return false;
		if (parent.equals(root))
			return true;
		return isParent(root, parent);
	}

	protected void postRefresh(final List toRefresh, final boolean updateLabels, Collection runnables) {
		runnables.add(new Runnable() {
			public void run() {
				for (Iterator iter= toRefresh.iterator(); iter.hasNext();) {
					fViewer.refresh(iter.next(), updateLabels);
				}
			}
		});
	}

	protected void postAdd(final Object parent, final Object element, Collection runnables) {
		runnables.add(new Runnable() {
			public void run() {
				if (fViewer.testFindItem(element) == null) 
					fViewer.add(parent, element);
				}
		});
	}

	protected void postRemove(final Object element, Collection runnables) {
		runnables.add(new Runnable() {
			public void run() {
				fViewer.remove(element);
			}
		});
	}

	protected void postProjectStateChanged(final Object root, Collection runnables) {
		runnables.add(new Runnable() {
			public void run() {
				fViewer.refresh(root, true);
				// trigger a synthetic selection change so that action refresh their
				// enable state.
				fViewer.setSelection(fViewer.getSelection());
			}
		});
	}
	
	
	/*
	 * @see org.eclipse.jface.util.IPropertyChangeListener#propertyChange(org.eclipse.jface.util.PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent event) {
		if (arePackagesFoldedInHierarchicalLayout() != fFoldPackages){
			fFoldPackages= arePackagesFoldedInHierarchicalLayout();
			if (fViewer != null && !fViewer.getControl().isDisposed()) {
				fViewer.getControl().setRedraw(false);
				Object[] expandedObjects= fViewer.getExpandedElements();
				fViewer.refresh();	
				fViewer.setExpandedElements(expandedObjects);
				fViewer.getControl().setRedraw(true);
			}
		}
	}
}
