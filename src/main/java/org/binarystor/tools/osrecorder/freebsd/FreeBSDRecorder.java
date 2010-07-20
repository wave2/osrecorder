/**
 * Copyright (c) 2008-2010 Wave2 Limited. All rights reserved.
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

package org.binarystor.tools.osrecorder.freebsd;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import org.binarystor.tools.osrecorder.osRecorder;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.Session;
import com.trilead.ssh2.StreamGobbler;

import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 *
 * @author Alan Snelson
 */
public class FreeBSDRecorder {

    private Map config;
    private boolean verbose = false;
    private Map<String, SVNRepository[]> svnRepositories;
    private String freebsdVersion;
    private Session sess;

    /**
     *
     * @param svnRepositories
     * @param config
     */
    public FreeBSDRecorder(Map<String, SVNRepository[]> svnRepositories, Map config) {
        //TODO check this
        this.svnRepositories = svnRepositories;
        this.config = config;
        this.verbose = osRecorder.verbose;
    }

    /**
     *
     * @param sess
     */
    public FreeBSDRecorder(Session sess) {
        this.sess = sess;
    }

    /**
     *
     * @return
     */
    public String record() {
        String notifyMessage = "";

        try {

            List<Map> hosts = (ArrayList) config.get("FreeBSD");
            for (Map host : hosts) {
                //Host identifier
                String freebsdName = (String) host.get("Name");
                if (freebsdName == null) {
                    freebsdName = InetAddress.getByName((String) host.get("Hostname")).getHostName();
                }
                if (verbose) {
                    System.out.println("-- Processing FreeBSD host " + freebsdName + " ---");
                }
                //Get the repository used to store the FreeBSD changes
                SVNRepository writeRepository = svnRepositories.get((String) host.get("Repository"))[0];
                SVNRepository readRepository = svnRepositories.get((String) host.get("Repository"))[1];
                ISVNEditor editor = writeRepository.getCommitEditor("osRecorder Update", null);
                editor.openRoot(-1);
                //For each FreeBSD host defined in the config file store in VCS
                SVNNodeKind nodeKind = readRepository.checkPath("osRecorder/FreeBSD/" + freebsdName, -1);
                if (nodeKind == SVNNodeKind.NONE) {
                    osRecorder.addDir(readRepository, writeRepository, editor, "osRecorder/FreeBSD/" + freebsdName);
                }

                //Check to see if we need an SSH connection
                Map secureShell = (HashMap) host.get("SecureShell");
                Connection conn = null;
                if (secureShell != null) {
                    if (verbose) {
                        System.out.println("Connecting via SSH as " + (String) secureShell.get("Username") + "@" + (String) secureShell.get("Hostname"));
                    }
                    conn = new Connection((String) secureShell.get("Hostname"));
                    conn.connect();
                    try {
                        //Try Public Key Authentication first
                        if ((String) secureShell.get("KeyFile") != null) {
                            conn.authenticateWithPublicKey((String) secureShell.get("Username"), new File((String) secureShell.get("KeyFile")), (String) secureShell.get("KeyPassword"));
                        } else {
                            conn.authenticateWithPassword((String) secureShell.get("Username"), (String) secureShell.get("Password"));
                        }
                    } catch (IOException ioe) {
                        if (ioe.getMessage().equals("Password authentication failed.")) {
                            System.out.println("Error: Password authentication failed.\nMany default SSH server installations are configured to refuse the authentication type 'password'. Often, they only accept 'publickey' and 'keyboard-interactive'.");
                        }
                        System.out.println(ioe.getMessage());
                    }
                    sess = conn.openSession();

                    //Save Kernel Parameters
                    String currentVariables = "";
                    Map<String, String> kernelParams = getKernelParameters();
                    for (Map.Entry<String, String> variable : kernelParams.entrySet()) {
                        currentVariables += variable.getKey() + " : " + variable.getValue() + "\n";
                    }
                    nodeKind = readRepository.checkPath("osRecorder/FreeBSD/" + freebsdName + "/GlobalVariables", -1);
                    if (nodeKind == SVNNodeKind.NONE) {
                        osRecorder.addFile(readRepository, writeRepository, editor, "osRecorder/FreeBSD/" + freebsdName + "/GlobalVariables", currentVariables.getBytes());
                    } else {
                        //Save Diff for notify
                        SVNProperties fileProperties = new SVNProperties();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        readRepository.getFile("osRecorder/FreeBSD/" + freebsdName + "/GlobalVariables", -1, fileProperties, baos);
                        String reposVariables = baos.toString();
                        if (!currentVariables.equals(reposVariables)) {
                            notifyMessage += "--- Kernel Parameters Modified on " + freebsdName + " ---\n\n" + osRecorder.printDiffs(currentVariables.split("\n"), reposVariables.split("\n")) + "\n\n";
                            //Update VCS Repository
                            osRecorder.modifyFile(writeRepository, editor, "osRecorder/FreeBSD/" + freebsdName, "osRecorder/FreeBSD/" + freebsdName + "/GlobalVariables", reposVariables.getBytes(), currentVariables.getBytes(), "Kernel Parameter Updated");
                        }
                    }

                    //Close SSH Tunnel if using SSH
                    if (secureShell != null) {
                        if (verbose) {
                            System.out.println("Closing SSH Tunnel to host " + (String) secureShell.get("Hostname"));
                        }
                        sess.close();
                        conn.close();
                    }
                    editor.closeEdit();

                }
            }

        } catch (UnknownHostException uhe) {
            System.out.println("Unknown host: " + uhe.getMessage() + "\nPlease check hostname is able to resolve using a tool like nslookup or dig.");
            System.exit(1);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return notifyMessage;
    }

    /**
     * Method to execute command
     *
     * @param sess SSH Session object
     * @return command output
     */
    private String executeCommand(String command) {
        String output = "";
        try {
            sess.execCommand(command);
            InputStream stdout = new StreamGobbler(sess.getStdout());
            BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
            String line;
            while ((line = br.readLine()) != null) {
                output += line + "\n";
            }
        } catch (IOException ioe) {
            System.out.println(ioe.getMessage());
        }
        return output;
    }

    /**
     * Method to obtain Kernel Parameters minus any dynamic params.
     *
     * @return Kernel Parameters
     */
    public Map<String, String> getKernelParameters() {
        Map<String, String> parameters = null;
        try {
            parameters = new TreeMap();
            for (String line : executeCommand("sysctl -a").split("\n")) {
                if (line.matches("^(\\S+\\.\\S+){1,}: .+")) {
                    parameters.put(line.split(": ")[0], line.split(": ")[1]);
                }
            }
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        if (verbose) {
            System.out.println(parameters);
        }
        //Remove variable dynamic parameters as they change with every request
        parameters.remove("debug.PMAP1changed");
        parameters.remove("debug.PMAP1unchanged");
        parameters.remove("debug.PMAP1changedcpu");
        parameters.remove("debug.dir_entry");
        parameters.remove("debug.direct_blk_ptrs");
        parameters.remove("debug.hashstat.nchash");
        parameters.remove("debug.indir_blk_ptrs");
        parameters.remove("debug.inode_bitmap");
        parameters.remove("debug.numcache");
        parameters.remove("debug.numcachehv");
        parameters.remove("debug.numneg");
        parameters.remove("debug.to_avg_depth");
        parameters.remove("debug.to_avg_gcalls");
        parameters.remove("debug.to_avg_mpcalls");
        parameters.remove("debug.to_avg_mtxcalls");
        parameters.remove("hw.acpi.thermal.tz0.temperature");
        parameters.remove("hw.usermem");
        parameters.remove("kern.cp_time");
        parameters.remove("kern.cp_times");
        parameters.remove("kern.ipc.nsfbufspeak");
        parameters.remove("kern.ipc.numopensockets");
        parameters.remove("kern.ipc.pipekva");
        parameters.remove("kern.lastpid");
        parameters.remove("kern.nselcoll");
        parameters.remove("kern.openfiles");
        parameters.remove("kern.timecounter.nnanouptime");
        parameters.remove("kern.timecounter.nbintime");
        parameters.remove("kern.timecounter.nbinuptime");
        parameters.remove("kern.timecounter.ngetbinuptime");
        parameters.remove("kern.timecounter.ngetmicrotime");
        parameters.remove("kern.timecounter.ngetmicrouptime");
        parameters.remove("kern.timecounter.ngetnanotime");
        parameters.remove("kern.timecounter.ngetnanouptime");
        parameters.remove("kern.timecounter.nmicrotime");
        parameters.remove("kern.timecounter.nmicrouptime");
        parameters.remove("kern.timecounter.nnanotime");
        parameters.remove("kern.timecounter.nsetclock");
        parameters.remove("kern.timecounter.tc.ACPI-safe.counter");
        parameters.remove("kern.timecounter.tc.TSC.counter");
        parameters.remove("kern.timecounter.tc.i8254.counter");
        parameters.remove("kern.tty_nin");
        parameters.remove("kern.tty_nout");
        parameters.remove("net.inet.tcp.pcbcount");
        parameters.remove("net.inet.tcp.hostcache.count");
        parameters.remove("net.inet.tcp.reass.overflows");
        parameters.remove("net.inet.tcp.sack.globalhole");
        parameters.remove("net.inet.tcp.hostcache.count");
        parameters.remove("net.isr.count");
        parameters.remove("net.isr.directed");
        parameters.remove("net.isr.queued");
        parameters.remove("net.isr.swi_count");
        parameters.remove("vfs.cache.dotdothits");
        parameters.remove("vfs.cache.dothits");
        parameters.remove("vfs.cache.nchstats");
        parameters.remove("vfs.cache.numcache");
        parameters.remove("vfs.cache.numcalls");
        parameters.remove("vfs.cache.numchecks");
        parameters.remove("vfs.cache.numneg");
        parameters.remove("vfs.cache.numnegzaps");
        parameters.remove("vfs.cache.numfullpathcalls");
        parameters.remove("vfs.cache.numfullpathfail1");
        parameters.remove("vfs.cache.numfullpathfound");
        parameters.remove("vfs.cache.nummiss");
        parameters.remove("vfs.cache.nummisszap");
        parameters.remove("vfs.cache.numneghits");
        parameters.remove("vfs.cache.numposhits");
        parameters.remove("vfs.cache.numposzaps");
        parameters.remove("vfs.flushwithdeps");
        parameters.remove("vfs.freevnodes");
        parameters.remove("vfs.getnewbufcalls");
        parameters.remove("vfs.numdirtybuffers");
        parameters.remove("vfs.numfreebuffers");
        parameters.remove("vfs.numvnodes");
        parameters.remove("vfs.reassignbufcalls");
        parameters.remove("vfs.recursiveflushes");
        parameters.remove("vfs.ufs.dirhash_mem");
        parameters.remove("vfs.worklist_len");
        parameters.remove("vm.pmap.pc_chunk_allocs");
        parameters.remove("vm.pmap.pc_chunk_count");
        parameters.remove("vm.pmap.pc_chunk_frees");
        parameters.remove("vm.pmap.pv_entry_allocs");
        parameters.remove("vm.pmap.pv_entry_count");
        parameters.remove("vm.pmap.pv_entry_frees");
        parameters.remove("vm.pmap.pv_entry_spare");
        parameters.remove("vm.stats.misc.zero_page_count");
        parameters.remove("vm.stats.object.bypasses");
        parameters.remove("vm.stats.object.bypasses");
        parameters.remove("vm.stats.object.collapses");
        parameters.remove("vm.stats.sys.v_intr");
        parameters.remove("vm.stats.sys.v_soft");
        parameters.remove("vm.stats.sys.v_swtch");
        parameters.remove("vm.stats.sys.v_syscall");
        parameters.remove("vm.stats.sys.v_trap");
        parameters.remove("vm.stats.vm.v_active_count");
        parameters.remove("vm.stats.vm.v_cache_count");
        parameters.remove("vm.stats.vm.v_cow_faults");
        parameters.remove("vm.stats.vm.v_forkpages");
        parameters.remove("vm.stats.vm.v_forks");
        parameters.remove("vm.stats.vm.v_free_count");
        parameters.remove("vm.stats.vm.v_inactive_count");
        parameters.remove("vm.stats.vm.v_intrans");
        parameters.remove("vm.stats.vm.v_pdpages");
        parameters.remove("vm.stats.vm.v_pdwakeups");
        parameters.remove("vm.stats.vm.v_reactivated");
        parameters.remove("vm.stats.vm.v_vnodein");
        parameters.remove("vm.stats.vm.v_vnodepgsin");
        parameters.remove("vm.stats.vm.v_ozfod");
        parameters.remove("vm.stats.vm.v_cow_optim");
        parameters.remove("vm.stats.vm.v_pfree");
        parameters.remove("vm.stats.vm.v_swapin");
        parameters.remove("vm.stats.vm.v_swapout");
        parameters.remove("vm.stats.vm.v_swappgsin");
        parameters.remove("vm.stats.vm.v_swappgsout");
        parameters.remove("vm.stats.vm.v_tcached");
        parameters.remove("vm.stats.vm.v_tfree");
        parameters.remove("vm.stats.vm.v_vforkpages");
        parameters.remove("vm.stats.vm.v_vforks");
        parameters.remove("vm.stats.vm.v_vnodeout");
        parameters.remove("vm.stats.vm.v_vnodepgsout");
        parameters.remove("vm.stats.vm.v_vm_faults");
        parameters.remove("vm.stats.vm.v_wire_count");
        parameters.remove("vm.stats.vm.v_zfod");
        return parameters;
    }

    /**
     * Method to obtain passwd file
     *
     * @return passwd file contents
     */
    public String getPasswd() {
        String passwd = null;
        try {
            passwd = executeCommand("cat /etc/passwd");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return passwd;
    }

    /**
     * Method to obtain group file
     *
     * @return group file contents
     */
    public String getGroup() {
        String group = null;
        try {
            group = executeCommand("cat /etc/group");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return group;
    }

    /**
     * Method to obtain filesystem definitions
     *
     * @return fstab file contents
     */
    public String getFilesystemsTable() {
        String fstab = null;
        try {
            fstab = executeCommand("cat /etc/fstab");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return fstab;
    }

    /**
     * Method to obtain mounted filesystems
     *
     * @return mounted fielsystems
     */
    public String getMountedFilesystems() {
        String mount = null;
        try {
            mount = executeCommand("mountb");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return mount;
    }

    /**
     * Method to obtain system mail aliases
     *
     * @return mail aliases
     */
    public String getSendmailAliases() {
        String aliases = null;
        try {
            aliases = executeCommand("cat /etc/aliases");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return aliases;
    }

    /**
     * Method to obtain contents of hosts file
     *
     * @return hosts file
     */
    public String getHosts() {
        String hosts = null;
        try {
            hosts = executeCommand("cat /etc/hosts");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return hosts;
    }

    /**
     * Method to obtain contents of nsswitch.conf
     *
     * @return name service switch
     */
    public String getNameServiceSwitch() {
        String nsswitch = null;
        try {
            nsswitch = executeCommand("cat /etc/nsswitch.conf");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return nsswitch;
    }

    /**
     * Method to obtain contents of resolv.conf
     *
     * @return resolver configuration
     */
    public String getFTPUsers() {
        String ftpusers = null;
        try {
            ftpusers = executeCommand("cat /etc/ftpusers");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return ftpusers;
    }

    /**
     * Method to obtain contents of resolv.conf
     *
     * @return resolver configuration
     */
    public String getResolverConfiguration() {
        String resolv = null;
        try {
            resolv = executeCommand("cat /etc/resolv.conf");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return resolv;
    }

    /**
     * Method to obtain contents of services file
     *
     * @return services
     */
    public String getServices() {
        String services = null;
        try {
            services = executeCommand("cat /etc/services");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return services;
    }

    /**
     * Method to obtain contents of nsswitch.conf
     *
     * @return name service switch
     */
    public String getSystemConfiguration() {
        String rcConf = null;
        try {
            rcConf = executeCommand("cat /etc/rc.conf");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return rcConf;
    }

    /**
     * Method to obtain network interface
     *
     * @return package list
     */
    public String getNetworkInterfaces() {
        String interfaces = null;
        try {
            interfaces = executeCommand("ipconfig");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return interfaces;
    }

    /**
     * Method to obtain list of software packages installed
     *
     * @return package list
     */
    public String getSoftwarePackages() {
        String packages = null;
        try {
            packages = executeCommand("pkg_info");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return packages;
    }

    /**
     * Method to obtain systemwide crontab
     *
     * @return crontab
     */
    public String getSystemCrontab() {
        String crontab = null;
        try {
            crontab = executeCommand("cat /etc/crontab");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return crontab;
    }

    /**
     * Method to obtain newsyslog.conf
     *
     * @return newsyslog
     */
    public String getLogRotate() {
        String newsyslog = null;
        try {
            newsyslog = executeCommand("cat /etc/newsyslog.conf");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return newsyslog;
    }

    /**
     * Method to obtain SSH Daemon Configuration
     *
     * @return sshdconfig
     */
    public String getSSHDConfig() {
        String sshdconfig = null;
        try {
            sshdconfig = executeCommand("cat /etc/ssh/sshd_config");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return sshdconfig;
    }

    /**
     * Method to obtain Hosts Access Control
     *
     * @return hostsAllow
     */
    public String getHostAccessControl() {
        String hostsAllow = null;
        try {
            hostsAllow = executeCommand("cat /etc/hosts.allow");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return hostsAllow;
    }

    /**
     * Method to obtain Hosts Access Control
     *
     * @return jails
     */
    public String getListActiveJails() {
        String jails = null;
        try {
            jails = executeCommand("jls");
        } catch (Exception ioe) {
            System.out.println(ioe.getMessage());
        }
        return jails;
    }
}
