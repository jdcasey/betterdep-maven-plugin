/*******************************************************************************
 * Copyright (c) 2014 Red Hat, Inc..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.commonjava.maven.plugins.betterdep;

import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.cartographer.data.CartoDataException;
import org.commonjava.maven.plugins.betterdep.impl.BetterDepRelationshipPrinter;

/**
 * Generates a tree-style listing of the artifacts contained within the dependency graph for
 * a project or set of projects. Output also includes parent POMs and BOMs referenced from
 * these artifacts by default. This output style is intended to show <em>how</em> different artifacts
 * were included in dependency graph, not just the fact of their inclusion.
 * 
 * If this goal is run using the -Dfrom=GAV[,GAV]* parameter,
 * those GAVs will be treated as the "roots" of the dependency graph (origins of traversal).
 * Otherwise, the current set of projects will be used.
 *  
 * @author jdcasey
 */
@Mojo( name = "tree", requiresProject = false, aggregator = true, threadSafe = true )
public class DepTreeGoal
    extends AbstractDepgraphGoal
{

    private static boolean HAS_RUN = false;

    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( HAS_RUN )
        {
            getLog().info( "Dependency tree has already run. Skipping." );
            return;
        }

        HAS_RUN = true;

        initDepgraph( true );
        resolveFromDepgraph();
        try
        {
            final Map<String, Set<ProjectVersionRef>> labels = getLabelsMap();

            final StringBuilder sb = new StringBuilder();
            for ( final ProjectVersionRef root : roots )
            {
                final Set<ProjectVersionRef> missing = carto.getDatabase()
                                                            .getAllIncompleteSubgraphs();

                final BetterDepRelationshipPrinter printer = new BetterDepRelationshipPrinter( missing );

                final String printed = carto.getRenderer()
                                            .depTree( root, filter, false, labels, printer );

                sb.append( "\n\n\nDependency tree for: " )
                  .append( root )
                  .append( ": \n\n" )
                  .append( printed );
            }

            write( sb );
        }
        catch ( final CartoDataException e )
        {
            throw new MojoExecutionException( "Failed to render dependency tree: " + e.getMessage(), e );
        }
    }
}
