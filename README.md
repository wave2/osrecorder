osRecorder
==========

When something breaks, the first question you ask is what changed?

Now you know.

Requirements
------------

* Java 1.6 (or above)
* Maven

Usage
-----

1\. Create a config file that defines the files / folders you wish to monitor for changes:

    datadir: c:\myOSRecorderDB
    notification:
    - method: smtp
      server: smtp.mymailserver.org
      port: 25
      sender: osRecorder@mymailserver.org
      recipients: [me@mymailserver.org]
    fileset:
    - name: MyWatchlist
      include:
      - C:\myfiles\*
      exclude:
      - C:\myfiles\TOPSECRET\*


2\. Run OSRecorder once to ensure your config wis valid and the datadir is created successfully:

    $  java -jar OSRecorder.jar -v -c myconfig.yml

3\. Schedule OSRecorder to run at your chosen interval (not every 5 seconds silly!).

Contributing
------------

Please do! Go on, don't be shy.

1. Create an [Issue] that clearly describes:
     * the problem you are trying to solve
     * an outline of your proposed solution
2. Wait a little while for any feedback
3. [Fork] osRecorder into your very own GitHub repository
4. Create a topic branch with a name corresponding to the issue number
   from step 1 e.g #XXX:
     * `$ git clone git@github.com/wave2/osrecorder.git my-osrecorder-repo`
     * `$ cd my-osrecorder-repo`
     * `$ git checkout -b osrecorder-XXX`
5. Commit your changes and include the issue number in your
   commit message:
     * `$ git commit -am "[#XXX] Added something cool"`
6. Push your changes to the branch:
     * `$ git push origin osrecorder-XXX`
7. Send a [Pull request] including the issue number in the subject

License
-------

Copyright &copy; 2008-2012 Wave2 Limited. All rights reserved. Licensed under [BSD License].

[BSD License]: https://github.com/wave2/osrecorder/raw/master/LICENSE
[Fork]: http://help.github.com/fork-a-repo
[Issue]: https://github.com/wave2/osrecorder/issues
[Pull request]: http://help.github.com/pull-requests
