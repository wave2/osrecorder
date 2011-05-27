/**
 * Copyright (c) 2008-2011 Wave2 Limited. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of Wave2 Limited nor the names of its contributors may be
 * used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.osrecorder;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.CorruptObjectException;
import static org.eclipse.jgit.lib.Constants.HEAD;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.api.errors.NoFilepatternException;

import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

/**
 *
 * @author Alan Snelson
 */
public class GitRepo implements org.osrecorder.Repository {

    private String dataDir;
    private Git repo;

    /**
     * Constructor
     *
     * @param  path Path to repository
     */
    GitRepo(String path) {
        initRepo(path);
    }

    public final void initRepo(String path) {
        InitCommand command = Git.init();
        command.setBare(false);
        if (path != null) {
            command.setDirectory(new File(path));
        }
        Repository repository = command.call().getRepository();
        this.repo = new Git(repository);
        this.dataDir = path;
    }

     /**
     * List files in repository
     *
     * @return List of files in repository
     */
    @Override
    public ArrayList<String> listFiles() {
        ArrayList<String> files = new ArrayList<String>();
        try {
            DirCache cache = repo.getRepository().readDirCache();
            for (int i = 0; i < cache.getEntryCount(); i++) {
                final DirCacheEntry ent = cache.getEntry(i);
                files.add(ent.getPathString().replace('/', File.separatorChar));
            }
        }
        catch (CorruptObjectException ex) {
            Logger.getLogger(GitRepo.class.getName()).log(Level.SEVERE, null, ex);
        }
        catch (IOException ex) {
            Logger.getLogger(GitRepo.class.getName()).log(Level.SEVERE, null, ex);
        }

        return files;
    }

     /**
     * Add file to repository
     *
     * @param  path Path to file
     */
    @Override
    public boolean processFile(String path) {
        System.out.println(path);
        try {
            repo.add().addFilepattern(path.replace(File.separatorChar, '/')).call();
        }
        catch (NoFilepatternException nfe) {
            System.err.println(nfe.getMessage());

        }
        return true;
    }
    
         /**
     * Remove file from repository
     *
     * @param  path Path to file
     */
    @Override
    public boolean removeFile(String path) {
        System.out.println(path);
        try {
            repo.rm().addFilepattern(path.replace(File.separatorChar, '/')).call();
        }
        catch (NoFilepatternException nfe) {
            System.err.println(nfe.getMessage());

        }
        return true;
    }

    /**
     * Save changes detected
     *
     * @return Success
     */
    @Override
    public boolean save() {
        try {
            repo.commit().setMessage("ttt").call();
        }
        catch (NoHeadException nhe) {
            System.err.println(nhe.getMessage());
        }
        catch (NoMessageException nme) {
            System.err.println(nme.getMessage());

        }
        catch (UnmergedPathException upe) {
            System.err.println(upe.getMessage());

        }
        catch (ConcurrentRefUpdateException crue) {
            System.err.println(crue.getMessage());

        }
        catch (WrongRepositoryStateException wrse) {
            System.err.println(wrse.getMessage());

        }
        return true;
    }

    /**
     * Get file differences
     *
     * @return List of differences
     */
    public String getDiffs() {
        ByteArrayOutputStream diffOutput = new ByteArrayOutputStream();
        try {
            //Test

            DiffFormatter diffFmt = new DiffFormatter(diffOutput);
            diffFmt.setRepository(repo.getRepository());
            AbstractTreeIterator oldTree;
            AbstractTreeIterator newTree;
            ObjectId head = repo.getRepository().resolve(HEAD + "^{tree}");
            if (head != null) {
                CanonicalTreeParser p = new CanonicalTreeParser();
                ObjectReader reader = repo.getRepository().newObjectReader();
                try {
                    p.reset(reader, head);
                }
                finally {
                    reader.release();
                }
                oldTree = p;
                newTree = new DirCacheIterator(repo.getRepository().readDirCache());
                diffFmt.format(oldTree, newTree);
                diffFmt.flush();
            }

            //End Test
        }
        catch (AmbiguousObjectException ex) {
            Logger.getLogger(GitRepo.class.getName()).log(Level.SEVERE, null, ex);
        }
        catch (IOException ex) {
            Logger.getLogger(GitRepo.class.getName()).log(Level.SEVERE, null, ex);
        }
        return diffOutput.toString();
    }

    @Override
    /**
     * Get repository directory
     *
     * @return Path to repository
     */
    public String getDataDir() {
        return this.dataDir;
    }
}
