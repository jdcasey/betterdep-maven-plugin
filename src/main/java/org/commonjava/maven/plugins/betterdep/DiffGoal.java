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

import static org.apache.commons.lang.StringUtils.join;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.commonjava.maven.atlas.graph.rel.ProjectRelationship;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.cartographer.data.CartoDataException;
import org.commonjava.maven.cartographer.dto.GraphDescription;
import org.commonjava.maven.cartographer.dto.GraphDifference;

/**
 * Generates pseudo-diff style output that highlights the differences between two
 * dependency graphs. The first (old) graph is generated from either the current projects,
 * or else the -Dfrom=GAV[,GAV]* parameter. The second (new) graph is generated 
 * from the -Dto=GAV[,GAV]* parameter.
 * 
 * This goal is most useful to determine the changes in the dependency graph from
 * one release of a project to the next. 
 * 
 * @author jdcasey
 */
@Mojo( name = "diff", requiresProject = false, aggregator = true, threadSafe = true )
public class DiffGoal
    extends AbstractDepgraphGoal
{

    public enum DiffFormat
    {
        brief, full, targets;
    }

    private static boolean HAS_RUN = false;

    @Parameter( property = "to", required = true )
    private String toProjects;

    @Parameter( defaultValue = "false", property = "reverse" )
    private boolean reverse;

    @Parameter( defaultValue = "brief", property = "format" )
    private String format;

    private Set<ProjectVersionRef> toGavs;

    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( HAS_RUN )
        {
            getLog().info( "Dependency diff has already run. Skipping." );
            return;
        }

        HAS_RUN = true;

        initDepgraph( true );
        resolveFromDepgraph();

        toGavs = toRefs( toProjects );
        getDirectRelsFor( toGavs );

        resolveDepgraph( filter, toGavs );

        getLog().info( "Resolving difference vs :\n\n  " + join( toGavs, "\n  " ) + "\n\nUsing scope: " + scope + "\n" );
        try
        {
            final GraphDescription f = new GraphDescription( filter, roots );
            final GraphDescription t = new GraphDescription( filter, toGavs );

            final GraphDifference<ProjectRelationship<?>> diff = carto.getCalculator()
                                                                      .difference( f, t );

            final StringBuilder sb = new StringBuilder();

            final List<String> removed = printRels( reverse ? diff.getAdded() : diff.getRemoved() );
            final List<String> added = printRels( reverse ? diff.getRemoved() : diff.getAdded() );

            Collections.sort( removed );
            Collections.sort( added );

            final List<String> rm = new ArrayList<String>( removed );
            rm.removeAll( added );
            added.removeAll( removed );

            for ( final String line : rm )
            {
                sb.append( "\n- " )
                  .append( line );
            }

            for ( final String line : added )
            {
                sb.append( "\n+ " )
                  .append( line );
            }

            sb.append( "\n\n" );

            write( sb );
        }
        catch ( final CartoDataException e )
        {
            throw new MojoExecutionException( "Failed to retrieve depgraph for roots: " + roots + ". Reason: " + e.getMessage(), e );
        }
    }

    private List<String> printRels( final Set<ProjectRelationship<?>> rels )
    {
        final Set<String> result = new LinkedHashSet<String>();
        final StringBuilder sb = new StringBuilder();
        for ( final ProjectRelationship<?> rel : rels )
        {
            sb.setLength( 0 );
            final DiffFormat fmt = format == null ? DiffFormat.brief : DiffFormat.valueOf( format.toLowerCase() );
            switch ( fmt )
            {
                case full:
                {
                    sb.append( rel );
                    break;
                }
                case brief:
                {
                    sb.append( rel.getDeclaring() )
                      .append( " -> " )
                      .append( rel.getTarget() );
                    break;
                }
                case targets:
                {
                    sb.append( rel.getTarget() );
                    break;
                }
            }
            result.add( sb.toString() );
        }

        return new ArrayList<String>( result );
    }
}
