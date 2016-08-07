package org.apache.zeppelin.notebook

import org.apache.zeppelin.conf.ZeppelinConfiguration
import org.apache.zeppelin.interpreter.InterpreterFactory
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

  def notebook

  def setup(){
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
    BINDING_INTERPRETERS_NUM * replFactory.setInterpreters(_,_)

    RESULT == notebook.notes.size()

    where:
    DESC | AUTO_INTERPRETER_BINDING | INTERPRETER_SETTING_LIST | BINDING_INTERPRETERS_NUM | RESULT
    "AUTO_INTERPRETER_BINDING == false" | false | null | 0 | 1
    "AUTO_INTERPRETER_BINDING == true" | true | ["interpreterId_TEST"] | 1 | 1
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
}
