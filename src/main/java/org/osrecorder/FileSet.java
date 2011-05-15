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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import org.osrecorder.config.FileSetConfig;

/**
 *
 * @author alan
 */
public class FileSet {

    private Repository repo;

    /**
     * Constructor
     *
     * @param   repo    Change repository
     */
    FileSet(Repository repo) {
        this.repo = repo;
    }

    /**
     * Check for modifications and store / report changes
     *
     * @param   fileSetConfig   FileSet configuration
     */
    public String processFileSet(FileSetConfig fileSetConf) {
        String result = "";
        boolean filesModified = false;
        String[] includes = fileSetConf.getInclude();
        for (String include : includes) {
            //Create file objects for source and destination
            File sourceFile = new File(include);
            File destFile = new File(repo.getDataDir() + File.separatorChar + sourceFile.getAbsolutePath().substring(sourceFile.getAbsolutePath().indexOf(File.separatorChar) + 1));
            //Create repo folders if not exisiting already
            File destFolder = new File(destFile.getParent());
            if (!destFolder.isDirectory() && !destFolder.exists()) {
                if (!new File(destFile.getParent()).mkdirs()) {
                    System.out.println("Failed to create repository folder: " + destFile.getParent());
                }
            }
            //Check if source file modification newer than repo copy
            if (sourceFile.lastModified() > destFile.lastModified()) {
                filesModified = true;
                //Copy file into repo
                try {
                    FileChannel inChannel = new FileInputStream(include).getChannel();
                    FileChannel outChannel = new FileOutputStream(destFile.getAbsolutePath()).getChannel();
                    inChannel.transferTo(0, inChannel.size(),
                            outChannel);
                }
                catch (IOException ioe) {
                    System.err.println(ioe.getMessage());
                }
                repo.processFile(sourceFile.getAbsolutePath().substring(sourceFile.getAbsolutePath().indexOf(File.separatorChar) + 1));
            }
        }
        if (filesModified) {
            result = repo.getDiffs();
            repo.save();
        }
        return result;
    }
}
