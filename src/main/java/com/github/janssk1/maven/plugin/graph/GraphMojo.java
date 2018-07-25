package com.github.janssk1.maven.plugin.graph;

import com.github.janssk1.maven.plugin.graph.domain.ArtifactRevisionIdentifier;
import com.github.janssk1.maven.plugin.graph.graph.Graph;
import com.github.janssk1.maven.plugin.graph.graphml.GraphMLGenerator;
import com.github.janssk1.maven.plugin.graph.graphml.SimpleVertexRenderer;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Goal which generates a set of dependency graphs
 */
@SuppressWarnings("unused")
@Mojo(name = "graph", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class GraphMojo extends AbstractMojo {

    @Component
    private ProjectBuilder mavenProjectBuilder;

    @Component
    private RepositorySystem repositorySystem;
    /**
     * A comma separated list of report definitions
     */
    @Parameter(property = "graph.reports", defaultValue = "PACKAGE,COMPILE,RUNTIME,TEST,COMPILE-TRANSITIVE")
    private String reports;

    @Parameter(property = "excludedGroupIds")
    private String[] excludedGroupIds;

    @Parameter(property = "excludedArtifactIds")
    private String[] excludedArtifactIds;

    @Parameter(property = "includeGroupId")
    private String includeGroupId;

    /**
     * shows the artifact version on the graph
     */
    @Parameter(defaultValue = "true")
    private boolean showVersion;

    /**
     * shows the edgesLabels on the graph
     */
    @Parameter(defaultValue = "true")
    private boolean showEdgeLabels;

    @Parameter(property = "project.groupId", readonly = true, required = true)
    private String groupId;

    @Parameter(property = "project.artifactId", readonly = true, required = true)
    private String artifactId;

    @Parameter(property = "project.version", readonly = true, required = true)
    private String version;

    @Parameter(property = "project.build.finalName", required = true)
    private String finalName;

    /**
     * Location of the file.
     */
    @Parameter(property = "project.build.directory", readonly = true, required = true)
    private File outputDirectory;

    /**
     * Maven's local repository.
     */
    @Parameter(property = "localRepository", readonly = true, required = true)
    private ArtifactRepository localRepository;

    /**
     * remote repositories
     */
    @Parameter(property = "project.remoteRepositories", readonly = true, required = true)
    private List<ArtifactRepository> remoteRepositories;


    public void execute() throws MojoExecutionException {

//        getLog().info("Using graph.reports=" + reports);
        getLog().info("Using includeGroupId=" + includeGroupId);
        getLog().info("Using excludedGroupIds=" + (excludedGroupIds == null ? "<null>" : StringUtils.join(excludedGroupIds, ",")));
        List<DependencyOptions> reportDefinitions = DependencyOptions.parseReportDefinitions(reports);
        ArtifactResolver artifactResolver = new MavenArtifactResolver(getLog(), localRepository, remoteRepositories, repositorySystem, mavenProjectBuilder);
        for (DependencyOptions reportDefinition : reportDefinitions) {
            buildGraph(artifactResolver, reportDefinition);
        }
    }

    private void buildGraph(ArtifactResolver artifactResolver, DependencyOptions options) throws MojoExecutionException {
        GraphBuilder graphBuilder = new BreadthFirstGraphBuilder(getLog(), artifactResolver, excludedGroupIds, excludedArtifactIds);
        Graph graph = graphBuilder.buildGraph(new ArtifactRevisionIdentifier(artifactId, groupId, version), options);
        GraphSerializer graphSerializer = new GraphMLGenerator();
        try {
            if (!outputDirectory.exists()) {
                boolean ignored = outputDirectory.mkdirs();
            }
            String outputFileName = this.artifactId + "-" + this.version + "-" + options.getGraphType()
                    + (options.isIncludeAllTransitiveDependencies() ? "-TRANSITIVE" : "");
            if (StringUtils.isNotBlank(finalName)) {
                outputFileName = finalName;
            }
            outputFileName += "-deps.graphml";
            File file = new File(outputDirectory, outputFileName);
            graphSerializer.serialize(graph, new FileWriter(file), new RenderOptions(new SimpleVertexRenderer(showVersion), showEdgeLabels));
            getLog().info("Created dependency graph in " + file);
        } catch (IOException e) {
            throw new MojoExecutionException("Can't write to file", e);
        }
    }

}
