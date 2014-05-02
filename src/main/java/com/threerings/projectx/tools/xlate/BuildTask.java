package com.threerings.projectx.tools.xlate;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;

import com.threerings.projectx.tools.xlate.AppUtils.FolderChoice;

import static com.threerings.projectx.tools.xlate.AppUtils.log;

public class BuildTask extends Task
{
    public void setGameClientFiles (String gameClientFiles)
    {
        _gameClientFiles = gameClientFiles;
    }

    public void setSupportFiles (String supportFiles)
    {
        _supportFiles = supportFiles;
    }

    public void setVerifyMacro (String verifyMacro)
    {
        _verifyMacro = verifyMacro;
    }

    public void setCommitChanges (boolean commitChanges)
    {
        _commitChanges = commitChanges;
    }

    public void setRemoveRows (boolean removeRows)
    {
        _removeRows = removeRows;
    }

    public void setCheckOnly (boolean checkOnly)
    {
        _checkOnly = checkOnly;
    }

    public void setUsername (String username)
    {
        _username = username;
    }

    public void setPassword (String password)
    {
        _password = password;
    }

    public void setProfile (String profile)
    {
        _profile = FolderChoice.valueOf(profile.toUpperCase());
    }

    public List<Domain> getDomains ()
    {
        List<Domain> domains = Lists.newArrayList(Domain.values());
        domains.remove(Domain.REGISTER_APP);
        domains.remove(Domain.SLING);
        domains.remove(Domain.SLING_EVENT);

        for (Domain domain : _profile.excluded) {
            domains.remove(domain);
        }
        return domains;
    }

    public Set<Language> getLanguages ()
    {
        return _profile.languages;
    }

    public boolean getSupportTool ()
    {
        return _profile.supportTool;
    }

    public boolean getPrependPlaceholder ()
    {
        return _profile.prependPlaceholder;
    }

    @Override
    public void execute ()
    {
        if (_username == null) {
            throw new BuildException("Expected a username attribute");
        }
        if (_password == null) {
            throw new BuildException("Expected a password attribute");
        }
        if (_profile == null) {
            throw new BuildException("Expected a profile attribute");
        }
        switch (_profile) {
        case CUSTOM: throw new BuildException("Unexpected profile " + _profile);
        default: break;
        }

        List<Exception> failures = Lists.newArrayList();
        for (Domain domain : getDomains()) {
            log.info("Translating " + domain);
            execute(domain, failures);
        }

        if (_commitChanges) {
            try {
                List<String> add = Lists.newArrayList("svn", "add", "-q");
                for (File dir : new File[] {new File(_gameClientFiles), new File(_supportFiles)}) {
                    for (File props : AppUtils.findProps(dir, true)) {
                        add.add(props.getPath());
                    }
                }

                run(new ProcessBuilder(add));
                run(new ProcessBuilder("svn", "commit", _gameClientFiles,
                    _supportFiles, "-m", "Updated translations"));

            } catch (InterruptedException ex) {
                getProject().log("Unable to commit changes");
                failures.add(ex);

            } catch (IOException ex) {
                getProject().log("Unable to commit changes");
                failures.add(ex);
            }
        }

        if (_verifyMacro.length() > 0) {
            log.info("Running verify macro (" + _verifyMacro + ")");
            try {
                URLConnection conn = new URL(_verifyMacro).openConnection();
                conn.setAllowUserInteraction(false);
                conn.setUseCaches(false);
                int timeout = 15 * 60 * 1000; // 15 minutes
                conn.setConnectTimeout(timeout);
                conn.setReadTimeout(timeout);
                conn.setDoOutput(false);
                copyToOutput(conn.getInputStream(), "Macro output: ");

            } catch (Exception ex) {
                getProject().log("Could not access verify macro URL: " + ex);
                failures.add(ex);
            }
        }

        if (failures.size() > 0) {
            log.error("Failure(s) occurred:");
            for (Exception failure : failures) {
                log.error("   " + failure);
            }
            throw new BuildException("Failure(s) occurred, see log");
        }
    }

    protected void run (ProcessBuilder command)
        throws InterruptedException, IOException
    {
        Process process = command.redirectErrorStream(true).start();
        process.getOutputStream().close();
        copyToOutput(process.getInputStream(), "svn: ");
        int result = process.waitFor();
        if (result != 0) {
            throw new IOException("Non-zero return code: " + result);
        }
    }

    protected void copyToOutput (InputStream istream, String prefix)
        throws IOException
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(istream));
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            log.info(prefix + line);
        }
    }

    protected void execute (Domain domain, List<Exception> failures)
    {
        DownloadLanguage.Parameters download = new DownloadLanguage.Parameters();
        setup(download, domain);
        download.noPrepend = !getPrependPlaceholder();

        AppUtils.Data data;
        try {
            data = download.requireData();

        } catch (Exception ex) {
            failures.add(ex);
            return;
        }

        DownloadLanguage.main(download, data);

        UploadEnglish.Parameters upload = new UploadEnglish.Parameters();
        setup(upload, domain);
        upload.remove = _removeRows;

        UploadEnglish.main(upload, data);

        failures.addAll(data.failures);
    }

    protected void setup (AppUtils.CommonParameters params, Domain domain)
    {
        params.folderChoice = _profile;
        params.languages = getLanguages();
        params.username = _username;
        params.password = _password;
        params.supportTool = getSupportTool();
        params.domain = domain;
        params.checkOnly = _checkOnly;
        boolean support = domain == Domain.SUPPORT;
        params.propsPath = new File(support ? _supportFiles : _gameClientFiles);
        params.recursive = support;
    }

    protected String _gameClientFiles = "rsrc/i18n";
    protected String _supportFiles = "projects/projectxsupport/src/gwt";
    protected String _verifyMacro = "https://script.google.com/macros/s/AKfycbzPN5fPX5laIByVQos7NGRsV9l83TSjuSrcjD3IPk5vfBkKG_Uf/exec";
    protected boolean _commitChanges;
    protected boolean _removeRows;
    protected boolean _checkOnly;

    protected String _username;
    protected String _password;
    protected FolderChoice _profile;
}
