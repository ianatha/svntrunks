package com.episparq.svntrunks;

import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class SVNTrunks {
    final String base;
    final SVNRepository repository;
    final DirectedGraph<String, DefaultEdge> graph = new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class);


    private void findTrunks(String location) throws SVNException {
        Collection<SVNDirEntry> entries = repository.getDir(location, -1, null, SVNDirEntry.DIRENT_KIND, (Collection) null);

        List<String> names = new ArrayList<String>(entries.size());
        int structuralDirsFound = 0;
        for (Iterator<SVNDirEntry> iterator = entries.iterator(); iterator.hasNext();) {
            SVNDirEntry entry = iterator.next();
            if (entry.getName().equals("trunk") || entry.getName().equals("tags") || entry.getName().equals("branches")) {
                structuralDirsFound++;
            }
        }

        if (structuralDirsFound == 3) {
            String previous = "/";
            graph.addVertex(previous);
            for (String segment : ((location + "/trunk").split("/"))) {
                if (segment.length() > 0) {
                    String current = previous + segment + "/";
                    graph.addVertex(current);
                    graph.addEdge(previous, current);
                    previous = current;
                }
            }
        } else {
            int traversed = 0;
            for (Iterator<SVNDirEntry> iterator = entries.iterator(); iterator.hasNext();) {
                SVNDirEntry entry = iterator.next();
                if (entry.getKind() == SVNNodeKind.DIR) {
                    String nextLocation = location +  "/" + entry.getRelativePath();
                    traversed++;
                    findTrunks(nextLocation);
                }
            }
            if (traversed == 0) {
                // out.println("# " + location + " ends without a full [trunk, tags, branches]!");
            }
        }
    }

    public SVNTrunks(String repositoryURL, PrintStream out) throws SVNException {
        SVNRepositoryFactoryImpl.setup();
        base = repositoryURL;
        repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(base));
        ISVNAuthenticationManager authManager = new DefaultSVNAuthenticationManager(
                null, true, null, "", null, "");
        repository.setAuthenticationManager(authManager);
        findTrunks("");

        out.println(String.format("svn co --depth=immediates %s && cd `basename !$`", base));
        for (Iterator<String> iterator = new TopologicalOrderIterator(graph); iterator.hasNext();) {
            String entry = iterator.next();
                if (!entry.equals("/")) {
                String depth;
                if (entry.endsWith("/trunk/")) {
                    depth = "infinity";
                } else {
                    depth = "immediates";
                }
                out.println(String.format("svn up --set-depth %s %s", depth, entry.replaceFirst(base, "")));
            }
        }
        out.println(String.format("svn up && svn st && cd .."));
    }

    public static void main(String[] args) throws Exception {
        new SVNTrunks(args[0], System.out);
    }
}
