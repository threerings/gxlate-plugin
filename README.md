gxlate-plugin
=============

A tool set and Maven plugin for uploading English properties bundles to a Google spreadsheet and downloading translated ones.

How to setup:
-----------------
1) Set up a google docs folder with a spreadsheet, like [this](https://drive.google.com/folderview?id=0B8jsTQHTM3a-WkNXWTRYSUkwcWM).

2) In the template tab, add columns for each language to be translated, matching the pattern in the sample (which just has FR).

3) Copy and rename the template tab for each properties file you need to localize. For example, if you have global.properties, create a copy of the tab named global.

4) Add the plugin to your maven project. It should look something like this, except with your project's property values:

    ...
    <properties>
      <gxlate.propsDir>src/main/resources/i18n</gxlate.propsDir>
      <gxlate.languages>FR</gxlate.languages>
      <gxlate.folderId>0B8jsTQHTM3a-WkNXWTRYSUkwcWM</gxlate.folderId>
    </properties>
    ...
    <build>
      ...
      <plugins>
        ...
        <plugin>
          <groupId>com.threerings.maven</groupId>
          <artifactId>gxlate-maven-plugin</artifactId>
          <version>1.0-SNAPSHOT</version>
        </plugin>
        ...
      </plugins>
    ...
    </build>

5) Add your Google username and password in your settings.xml (or anywhere else maven allows):

    <properties>
      <google.username>someone@gmail.com</google.username>
      <google.password>XXXX</google.password>
    </properties>

6) (Optional) get additional help in the usual way:

    mvn help:describe -Dplugin=com.threerings.maven:gxlate-maven-plugin -Ddetail

How to use:
----
1) Upload all your new and changed English strings to the spreadsheet:

    mvn gxlate:upload

2) Get your translators or beta testers to input new strings in appropriate languages. Note that they need to clear the value from the "Verified" column to denote that the text is ready to use.

3) Download new strings into localized bundles

    mvn gxlate:download

