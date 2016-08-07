package org.apache.zeppelin.notebook

import org.apache.zeppelin.conf.ZeppelinConfiguration
import org.apache.zeppelin.interpreter.InterpreterFactory
import org.apache.zeppelin.interpreter.InterpreterSetting
import org.apache.zeppelin.notebook.repo.NotebookRepo
import org.apache.zeppelin.scheduler.SchedulerFactory
import org.apache.zeppelin.search.SearchService
import org.apache.zeppelin.user.AuthenticationInfo
import org.apache.zeppelin.user.Credentials
import spock.lang.Specification
import spock.lang.Unroll

class NotebookSpockTest extends Specification {
  def schedulerFactory = Mock(SchedulerFactory)
  def replFactory = Mock(InterpreterFactory)
  def conf = Mock(ZeppelinConfiguration)
  def jobListenerFactory = Mock(JobListenerFactory)
  def notebookRepo = Mock(NotebookRepo)
  def notebookIndex = Mock(SearchService)
  def notebookAuthorization = Mock(NotebookAuthorization)
  def credentials = Mock(Credentials)

  Notebook notebook

  def setup() {
    notebookRepo.list(_) >> []
    notebook = new Notebook(conf, notebookRepo, schedulerFactory, replFactory, jobListenerFactory, notebookIndex, notebookAuthorization, credentials)
  }

  @Unroll
  def "createNote : #DESC"() {
    given:
    conf.getBoolean(_) >> AUTO_INTERPRETER_BINDING
    replFactory.getDefaultInterpreterSettingList() >> INTERPRETER_SETTING_LIST
    replFactory.getInterpreterSettings(_) >> []

    when:
    notebook.createNote(new AuthenticationInfo())

    then:
    2 * notebookIndex.addIndexDoc(_) // this method is called 2 times.
    BINDING_INTERPRETERS_NUM * replFactory.setInterpreters(_, _)

    RESULT == notebook.notes.size()

    where:
    DESC                                | AUTO_INTERPRETER_BINDING | INTERPRETER_SETTING_LIST | BINDING_INTERPRETERS_NUM | RESULT
    "AUTO_INTERPRETER_BINDING == false" | false                    | null                     | 0                        | 1
    "AUTO_INTERPRETER_BINDING == true"  | true                     | ["interpreter1"]         | 1                        | 1
  }

  def "exportNote : if note is exist"() {
    given:
    notebook.notes.put("note1", new Note())

    when:
    notebook.exportNote("note1")

    then:
    noExceptionThrown()
  }

  def "exportNote : if note is not exist"() {
    when:
    notebook.exportNote("note1")

    then:
    def e = thrown(IllegalArgumentException)
    e.getMessage() == "note1 not found"
  }

  @Unroll
  def "importNote : #DESC"() {
    given:
    Note note = notebook.createNote(new AuthenticationInfo())
    note.setName("oldNoteName")

    if (EXIST_PARAGRAPH) {
      note.addParagraph()
    }

    def sourceJson = notebook.exportNote(note.getId())

    when:
    Note result = notebook.importNote(sourceJson, NEW_NOTE_NAME, new AuthenticationInfo())

    then:
    result.getName() == RESULT_NOTE_NAME
    result.getParagraphs().size() == RESULT_PARAGRAPHS_SIZE //validate if the old note's paragraph is copied to new note

    where:
    DESC                       | NEW_NOTE_NAME | RESULT_NOTE_NAME | EXIST_PARAGRAPH | RESULT_PARAGRAPHS_SIZE
    "if new noteName is null"  | null          | "oldNoteName"    | false           | 0
    "if new noteName is exist" | "newNoteName" | "newNoteName"    | false           | 0
    "if paragraph is exist"    | "newNoteName" | "newNoteName"    | true            | 1
  }

  @Unroll
  def "cloneNote : #DESC"() {
    given:
    Note sourceNote = new Note()
    sourceNote.setName("sourceNoteName")

    if (EXIST_PARAGRAPH) {
      sourceNote.addParagraph()
    }

    notebook.notes.put("sourceNoteName", sourceNote)

    replFactory.getInterpreters(_) >> []
    replFactory.getInterpreterSettings(_) >> []

    when:
    Note result = notebook.cloneNote(SOURCE_NOTE_ID, NEW_NOTE_NAME, new AuthenticationInfo())

    then:
    result.getName() == RESULT_NOTE_NAME
    result.getParagraphs().size() == RESULT_PARAGRAPHS_SIZE

    where:
    DESC                       | SOURCE_NOTE_ID   | NEW_NOTE_NAME | RESULT_NOTE_NAME | EXIST_PARAGRAPH | RESULT_PARAGRAPHS_SIZE
    "if new noteName is null"  | "sourceNoteName" | null          | ""               | false           | 0
    "if new noteName is exist" | "sourceNoteName" | "newNoteName" | "newNoteName"    | false           | 0
    "if paragraph is exist"    | "sourceNoteName" | "newNoteName" | "newNoteName"    | true            | 1
  }

  def "cloneNote : if sourceNote is not exist"() {
    when:
    notebook.cloneNote("sourceNote1", "newNoteName", new AuthenticationInfo())

    then:
    def e = thrown(IllegalArgumentException)
    e.getMessage() == "sourceNote1 not found"
  }

  @Unroll
  def "bindInterpretersToNote : #DESC"() {
    given:
    notebook.notes.put("note1", new Note())

    replFactory.getInterpreterSettings(_) >> CURRENT_BINDINGS

    when:
    notebook.bindInterpretersToNote(NOTE_ID, INTERPRETER_SETTING_IDS)

    then:
    BINDING_INTERPRETERS_NUM * replFactory.setInterpreters(_, _)


    where:
    DESC                                                      | NOTE_ID | INTERPRETER_SETTING_IDS | CURRENT_BINDINGS                                    | BINDING_INTERPRETERS_NUM
    "if note is not exist"                                    | "note2" | null                    | null                                                | 0
    "if note is exist"                                        | "note1" | []                      | []                                                  | 1
    "if INTERPRETER_SETTING_IDS not contains currentBindings" | "note1" | ["interpreterSetting1"] | [new InterpreterSetting(id: "interpreterSetting2")] | 1
    "if INTERPRETER_SETTING_IDS contains currentBindings"     | "note1" | ["interpreterSetting1"] | [new InterpreterSetting(id: "interpreterSetting1")] | 1
  }

  @Unroll
  def "getBindedInterpreterSettingsIds"() {
    given:
    notebook.notes.put("note1", new Note())
    replFactory.getInterpreters(_) >> ["interpreter1"]

    when:
    def result = notebook.getBindedInterpreterSettingsIds(NOTE_ID)

    then:
    result == RESULT

    where:
    DESC                   | NOTE_ID | RESULT
    "if note is exist"     | "note1" | ["interpreter1"]
    "if note is not exist" | "note2" | []
  }

  @Unroll
  def "getBindedInterpreterSettings"() {
    given:
    notebook.notes.put("note1", new Note())
    replFactory.getInterpreterSettings(_) >> [new InterpreterSetting()]

    when:
    def result = notebook.getBindedInterpreterSettings(NOTE_ID)

    then:
    result.size() == RESULT

    where:
    DESC                   | NOTE_ID | RESULT
    "if note is exist"     | "note1" | 1
    "if note is not exist" | "note2" | 0
  }

  

}
