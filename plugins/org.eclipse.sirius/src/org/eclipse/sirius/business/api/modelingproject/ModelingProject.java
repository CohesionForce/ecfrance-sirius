/*******************************************************************************
 * Copyright (c) 2011 THALES GLOBAL SERVICES.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.business.api.modelingproject;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.business.api.session.SessionManager;
import org.eclipse.sirius.business.internal.query.ModelingProjectQuery;
import org.eclipse.sirius.ext.base.Option;
import org.eclipse.sirius.ext.base.Options;

/**
 * A modeling project nature is used to know which projects should be handled in
 * project mode by the modeling explorer.
 * 
 * @author mchauvin
 */
public class ModelingProject implements IProjectNature, IModelingElement {

    /**
     * error message when no representation file found.
     */
    public static final String ZERO_REPRESENTATIONS_FILE_FOUND_IN = "Zero representations file found in \"";

    /** The nature id. */
    public static final String NATURE_ID = "org.eclipse.sirius.nature.modelingproject";

    /** The default name for the representations file of a modeling project. */
    public static final String DEFAULT_REPRESENTATIONS_FILE_NAME = "representations.aird";

    /** the project on which the nature is applied. */
    private IProject project;

    /**
     * The URI of the main session file of this project.
     */
    private URI mainRepresentationsFileURI;

    /**
     * A modeling project is not valid if its main representations file has not
     * be loaded correctly.
     */
    private boolean valid = true;

    /**
     * Default constructor necessary for reflection instantiation.
     */
    public ModelingProject() {

    }

    /**
     * {@inheritDoc}
     * 
     * @see org.eclipse.core.resources.IProjectNature#configure()
     */
    public void configure() throws CoreException {
        /* do nothing */
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.eclipse.core.resources.IProjectNature#deconfigure()
     */
    public void deconfigure() throws CoreException {
        /* do nothing */
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.eclipse.core.resources.IProjectNature#getProject()
     */
    public IProject getProject() {
        return project;
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.eclipse.core.resources.IProjectNature#setProject(org.eclipse.core.resources.IProject)
     */
    public void setProject(IProject project) {
        this.project = project;
    }

    /**
     * Check if the given project is accessible and it has a modeling project
     * nature.
     * 
     * @param project
     *            the project to check
     * @return <code>true</code> if it has, <code>false</code> otherwise
     */
    public static boolean hasModelingProjectNature(IProject project) {
        try {
            return project.exists() && project.hasNature(ModelingProject.NATURE_ID);
        } catch (CoreException e) {
            // project does not exist or is not open
        }
        return false;
    }

    /**
     * Get the corresponding Modeling project.
     * 
     * @param project
     *            The original project
     * @return an optional ModelingProject (none if this project is not a
     *         modeling project).
     */
    public static Option<ModelingProject> asModelingProject(IProject project) {
        IProjectNature nature = null;

        if (project != null) {
            try {
                nature = project.getNature(ModelingProject.NATURE_ID);
            } catch (CoreException e) {
                /* does nothing */
            }
        }

        if (nature instanceof ModelingProject) {
            return Options.newSome((ModelingProject) nature);
        }

        return Options.newNone();
    }

    /**
     * Retrieve the opened session in this project.
     * 
     * @return the opened session associated to this project, <code>null</code>
     *         if it can not be found or if session is not yet opened
     */
    public Session getSession() {
        /*
         * this method should remain fastest as possible : - the number of aird
         * file for a a project should be very low, most often there will be
         * only one.
         */
        final Option<URI> optionalUri = getMainRepresentationsFileURI(new NullProgressMonitor());
        if (optionalUri.some()) {
            for (Session session : SessionManager.INSTANCE.getSessions()) {
                if (optionalUri.get().equals(session.getSessionResource().getURI())) {
                    return session;
                }
            }
        }
        return null;
    }

    /**
     * Return an optional URI corresponding to the main representations file of
     * this project. If the main representations file is not known, it will be
     * computed by a specific SaxParser that analyzes representations files of
     * this project to determine which is never referenced.
     * 
     * @param monitor
     *            the monitor to be used for reporting progress and responding
     *            to cancelation. The monitor is never <code>null</code>
     * @return an optional URI corresponding to the main session file of this
     *         project.
     * @throws IllegalArgumentException
     *             In case of multiples main aird in the references.
     */
    public Option<URI> getMainRepresentationsFileURI(IProgressMonitor monitor) throws IllegalArgumentException {
        return getMainRepresentationsFileURI(monitor, false, false);
    }

    /**
     * Return an optional URI corresponding to the main representations file of
     * this project. If the main representations file is not known, it will be
     * computed by a specific SaxParser that analyzes representations files of
     * this project to determine which is never referenced.
     * 
     * @param monitor
     *            the monitor to be used for reporting progress and responding
     *            to cancellation. The monitor is never <code>null</code>
     * @param force
     *            true if the main representations file must be compute even if
     *            it is already known.
     * @param throwException
     *            true if you want to throw exception in case of problem or only
     *            have an None option result.
     * @return an optional URI corresponding to the main session file of this
     *         project.
     * @throws IllegalArgumentException
     *             In case of problem during computing the main representations
     *             file.
     */
    public Option<URI> getMainRepresentationsFileURI(IProgressMonitor monitor, boolean force, boolean throwException) throws IllegalArgumentException {
        Option<URI> mainRepresentationsFileURIOption = Options.newNone();
        try {
            monitor.beginTask("Get main representations resource URI", 1);
            if (force) {
                setValid(true);
                mainRepresentationsFileURI = null;
            }
            if (valid && mainRepresentationsFileURI == null) {
                // The main representations file is not known (or is known but
                // must
                // be recompute). We must compute it.
                try {
                    Option<URI> result = new ModelingProjectQuery(this).computeMainRepresentationsFileURI(new SubProgressMonitor(monitor, 1));
                    if (result.some()) {
                        mainRepresentationsFileURI = result.get();
                    } else {
                        throw new IllegalArgumentException(ZERO_REPRESENTATIONS_FILE_FOUND_IN + getProject().getName() + "\". A modeling project must contain one.");
                    }
                } catch (IllegalArgumentException e) {
                    if (throwException) {
                        throw e;
                    }
                }
            }
        } finally {
            monitor.done();
        }
        if (mainRepresentationsFileURI != null) {
            mainRepresentationsFileURIOption = Options.newSome(mainRepresentationsFileURI);
        }
        return mainRepresentationsFileURIOption;
    }

    /**
     * Check if this representations file is the main representations file of
     * this project.
     * 
     * @param representationsFile
     *            the file to check
     * @return true if this file is the main representations file, false
     *         otherwise
     */
    public boolean isMainRepresentationsFile(IFile representationsFile) {
        boolean result = false;
        Option<URI> optionalMainRepresentationsFile = getMainRepresentationsFileURI(new NullProgressMonitor());
        if (optionalMainRepresentationsFile.some()) {
            result = optionalMainRepresentationsFile.get().equals(URI.createPlatformResourceURI(representationsFile.getFullPath().toString(), true));
        }
        return result;
    }

    /**
     * Get the valid status of this project.A modeling project is not valid if
     * its main representations file has not be loaded correctly.
     * 
     * @return true is the Modeling project is valid, false otherwise.
     */
    public boolean isValid() {
        return this.valid;
    }

    /**
     * Set the valid status of a modeling project. A modeling project is not
     * valid if its main representations file has not be loaded correctly.
     * 
     * @param valid
     *            The new valid state
     */
    public void setValid(boolean valid) {
        this.valid = valid;
    }
}
