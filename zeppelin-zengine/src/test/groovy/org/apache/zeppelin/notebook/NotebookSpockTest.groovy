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
    "AUTO_INTERPRETER_BINDING == true"  | true                     | ["interpreterId_TEST"]   | 1                        | 1
  }

  def "exportNote : if note is exist"() {
    given:
    notebook.notes.put("id_TEST", new Note())

    when:
    notebook.exportNote("id_TEST")

    then:
    noExceptionThrown()
  }

  def "exportNote : if note is not exist"() {
    when:
    notebook.exportNote("id_TEST")

    then:
    def e = thrown(IllegalArgumentException)
    e.getMessage() == "id_TEST not found"
  }

  @Unroll
  def "importNote : #DESC"() {
    given:
    Note note = notebook.createNote(new AuthenticationInfo())
    note.setName("oldNoteName_TEST")

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
    DESC                       | NEW_NOTE_NAME      | RESULT_NOTE_NAME   | EXIST_PARAGRAPH | RESULT_PARAGRAPHS_SIZE
    "if new noteName is null"  | null               | "oldNoteName_TEST" | false           | 0
    "if new noteName is exist" | "newNoteName_TEST" | "newNoteName_TEST" | false           | 0
    "if paragraph is exist"    | "newNoteName_TEST" | "newNoteName_TEST" | true            | 1
  }

  @Unroll
  def "cloneNote : #DESC"() {
    given:
    Note sourceNote = new Note()
    sourceNote.setName("sourceNoteName_TEST")

    if (EXIST_PARAGRAPH) {
      sourceNote.addParagraph()
    }

    notebook.notes.put("sourceNoteName_TEST", sourceNote)

    replFactory.getInterpreters(_) >> []
    replFactory.getInterpreterSettings(_) >> []

    when:
    Note result = notebook.cloneNote(SOURCE_NOTE_ID, NEW_NOTE_NAME, new AuthenticationInfo())

    then:
    result.getName() == RESULT_NOTE_NAME
    result.getParagraphs().size() == RESULT_PARAGRAPHS_SIZE

    where:
    DESC                       | SOURCE_NOTE_ID        | NEW_NOTE_NAME | RESULT_NOTE_NAME | EXIST_PARAGRAPH | RESULT_PARAGRAPHS_SIZE
    "if new noteName is null"  | "sourceNoteName_TEST" | null          | ""               | false           | 0
    "if new noteName is exist" | "sourceNoteName_TEST" | "newNoteName" | "newNoteName"    | false           | 0
    "if paragraph is exist"    | "sourceNoteName_TEST" | "newNoteName" | "newNoteName"    | true            | 1
  }

  def "cloneNote : if sourceNote is not exist"() {
    when:
    notebook.cloneNote("TEST", "newNoteName", new AuthenticationInfo())

    then:
    def e = thrown(IllegalArgumentException)
    e.getMessage() == "TEST not found"
  }

  @Unroll
  def "bindInterpretersToNote"() {
    given:
    notebook.notes.put("id_TEST", new Note())

    replFactory.getInterpreterSettings(_) >> CURRENT_BINDINGS

    when:
    notebook.bindInterpretersToNote(NOTE_ID, INTERPRETER_SETTING_IDS)

    then:
    BINDING_INTERPRETERS_NUM * replFactory.setInterpreters(_, _)


    where:
    DESC                                                      | NOTE_ID    | INTERPRETER_SETTING_IDS | CURRENT_BINDINGS                                    | BINDING_INTERPRETERS_NUM
    "if note is not exist"                                    | "wrong_Id" | null                    | null                                                | 0
    "if note is exist"                                        | "id_TEST"  | []                      | []                                                  | 1
    "if INTERPRETER_SETTING_IDS not contains currentBindings" | "id_TEST"  | ["interpreterSetting1"] | [new InterpreterSetting(id: "interpreterSetting2")] | 1
    "if INTERPRETER_SETTING_IDS contains currentBindings"     | "id_TEST"  | ["interpreterSetting1"] | [new InterpreterSetting(id: "interpreterSetting1")] | 1
  }


}
