import java.io.{OutputStreamWriter, FileOutputStream}
import scala.xml._

import surveys.SurveyClasses._
import surveys.DataImporter.DataImporter
import surveys.StatsGenerator.{Stats, StatsGenerator}

object GenerateReport {
  var next_tag_id: Int = 1

  def show_mean(s: Stats): NodeSeq =
      <span style="white-space: nowrap">
        {show_double(s.mean)}
        <span style="font-size: 0.7em" title="Odchylenie standardowe">(&sigma;: {show_double(s.dev)})</span>
      </span>

  def show_double(d: Double): String =
      "%2.3f" format d

  def dumpForSparkbar(s: Stats, domain: Seq[Int]): NodeSeq =
      <span class="inlinesparkbar">{
          val grouped = s.xs.groupBy(identity) mapValues (_.size)
          (for (x <- domain) yield grouped.getOrElse(x, 0)).mkString(",")
      }</span>

  def show_question_stats(s: Stats): NodeSeq =
      show_mean(s) ++ dumpForSparkbar(s, 1 to 7)

  def show_attendance_stats(s: Stats): NodeSeq =
      show_mean(s) ++ dumpForSparkbar(s, 5 to 95 by 10)

  def show_comments(comments: List[String]): NodeSeq =
    Seq(<div class="comments">{
      for(comment <- comments) yield Seq(<span class="comment">{ comment }</span>)
    }</div>)

  def show_comments_link(comments: List[String], id: String): NodeSeq =
    if(comments.nonEmpty){
        <a href="#" onClick={ "$(\"#comments-" ++ id ++ "\").toggle(); return(false);" }>
          Pokaż({ comments.size })
        </a>
    } else { new Text("Brak") }

  def showPartialMatrix[T](m: PartialMatrix[T], default: NodeSeq, triangleOnly: Boolean = false)(f: T => NodeSeq): NodeSeq = {
    <table>
      <tbody>
        <tr>
          <td>&nbsp;</td>
          {
            for ((_, index) <- m.labels.zipWithIndex) yield <th style="font-size: 0.6em">{"(" + index + ")"}</th>
          }
        </tr>
        {
          for ((label1, index1) <- m.labels.zipWithIndex) yield
            <tr>
              <th>{label1} <span style="font-size: 0.6em">({index1})</span></th>
              {
                for ((label2, index2) <- m.labels.zipWithIndex) yield
                  if (index1 < index2 && triangleOnly)
                    new Text(" ")
                  else
                    <td style="padding: 4px; white-space: nowrap;">{m.values.get(label1 -> label2).map(f).getOrElse(default)}</td>
              }
            </tr>
        }
      </tbody>
    </table>
  }

  def getUniqueId(): Int = {
    next_tag_id = next_tag_id + 1
    next_tag_id
  }

  def main(args: Array[String]){
		val salt = if (args contains "md5") Some(scala.util.Random.nextInt) else None
    salt foreach { x =>
      val fw = new OutputStreamWriter(new FileOutputStream("salt_KEEP_SECRET.txt"), "UTF-8")
      fw.write(x.toString)
      fw.close()
    }
    val answers = DataImporter.readSurveys(salt)
    val fw = new OutputStreamWriter(new FileOutputStream("Report.html"), "UTF-8")
    val statsByQuestion = StatsGenerator.statsByQuestion(answers).toList
    val statsByClassType = StatsGenerator.statsByClassType(answers).toList.sortBy(-_._2._2.mean)
    val statsByTitle = StatsGenerator.statsByTitle(answers).toList.sortBy(-_._2._2.mean).filter(_._2._1.sample_size >= 50)
    val statsByPosition = StatsGenerator.statsByPosition(answers).toList.sortBy(-_._2._2.mean).filter(_._2._1.sample_size >= 50)
    val statsByAggregatedPosition = StatsGenerator.statsByAggregatedPosition(answers).toList.sortBy(-_._2._2.mean).filter(_._2._1.sample_size >= 50)
    val statsByPersonSubject = StatsGenerator.statsByPersonSubject(answers).toList.sortBy(-_._2._2.mean).filter(_._2._1.sample_size >= 7)
    val statsByQuestionMatrix = StatsGenerator.statsByQuestionMatrix(answers)
    def show_per_person_stats(xs: List[((Person, Subject), (Stats, Stats))]): NodeSeq =
      <table>
        <thead>
          <tr>
            <th>Osoba</th>
            <th>Przedmiot</th>
            <th>Oceny</th>
            <th>Obecność (%)</th>
            <th>Próbka</th>
            <th>Komentarze</th>
          </tr>
        </thead>
        <tbody>
          {
            for(((person, subject), (attendance, questions)) <- xs) yield {
              val comments = StatsGenerator.getCommentsForPersonSubject(answers, person, subject)
              val comments_block_id = getUniqueId.toString
              <tr>
                <th>{ person }</th>
                <td>{ subject }</td>
                <td>{ show_question_stats(questions) }</td>
                <td>{ show_attendance_stats(attendance) }</td>
                <td>{ attendance.sample_size }</td>
                <td>{ show_comments_link(comments, comments_block_id) }</td>
              </tr>
              <tr class="comments" id={ "comments-" ++ comments_block_id }>
                <td colspan="6">
                  { show_comments(comments) }
                </td>
              </tr>
            }
          }
        </tbody>
      </table>
    val report =
      <html>
        <head>
          <meta http-equiv="content-type" content="text/html; charset=utf-8"/>
          <link rel="stylesheet" type="text/css" href="templates/style.css"/>
          <script type="text/javascript" src="templates/jquery-1.4.3.js"></script>
          <script type="text/javascript" src="templates/jquery.sparkline.js"></script>

          <script type="text/javascript">
              $(function() {{
                  /* Use 'html' instead of an array of values to pass options
                  to a sparkline with data in the tag */
                  $('.inlinesparkbar').sparkline('html', {{type: 'bar', barColor: 'blue'}});
              }});
          </script>
        </head>
        <body>
          <!-- github ribbon -->
          <a href="http://github.com/rawicki/ankiety"><img style="position: absolute; top: 0; left: 0; border: 0;" src="http://s3.amazonaws.com/github/ribbons/forkme_left_green_007200.png" alt="Fork me on GitHub" /></a>
          <h1>Wyniki ankiet 2009Z</h1>
          <h3>(wypełnionych ankiet: {answers.size})</h3>
          <div class="center">
            <h2>Średni wynik wg pytania</h2>
            <table>
              <thead>
                <tr>
                  <th>Pytanie</th>
                  <th>Średnia</th>
                </tr>
              </thead>
              <tbody>
                {
                  for((label, stats) <- statsByQuestion.sortBy(_._2.mean)) yield
                    <tr>
                    <th>{ label }</th>
                    <td>{ show_mean(stats) }</td>
                    </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="center">
            <h2>Korelacja pomiędzy wynikami z pytań</h2>
            {
              showPartialMatrix(statsByQuestionMatrix, new Text("-"), true) {
                case (stats1, stats2) => {
                  val cor = stats1 correlationWith stats2
                  if (cor < 0.75 || cor > 0.99)
                    <span style="color: gray">{show_double(cor)}</span>
                  else
                    new Text(show_double(cor))
                }
              }
            }
          </div>
          <div class="center">
            <h2>Średni wynik dla wszystkich pytań wg stopnia lub tytułu naukowego</h2>
            <table>
              <thead>
                <tr>
                  <th>X</th>
                    {
                      for((label, _) <- statsByTitle) yield
                        <th>{ label }</th>
                    }
                  </tr>
              </thead>
              <tbody>
                <tr>
                  <th>Pytania</th>
                  {
                    for((_, (attendance, questions)) <- statsByTitle) yield
                      <td>{ show_question_stats(questions) }</td>

                  }
                </tr>
                <tr>
                  <th>Obecności (%)</th>
                  {
                    for((_, (attendance, questions)) <- statsByTitle) yield
                      <td>{ show_attendance_stats(attendance) }</td>
                  }
                </tr>
                <tr>
                  <th>Ile próbek</th>
                  {
                    for((_, (attendance, questions)) <- statsByTitle) yield
                      <td>{ attendance.sample_size }</td>
                  }
                </tr>
              </tbody>
            </table>
          </div>
          <div class="center">
            <h2>Średni wynik dla wszystkich pytań wg rodzaju stanowiska</h2>
            <table>
              <thead>
                <tr>
                  <th>X</th>
                    {
                      for((label, _) <- statsByAggregatedPosition) yield
                        <th>{ label }</th>
                    }
                  </tr>
              </thead>
              <tbody>
                <tr>
                  <th>Pytania</th>
                  {
                    for((_, (attendance, questions)) <- statsByAggregatedPosition) yield
                      <td>{ show_question_stats(questions) }</td>
                  }
                </tr>
                <tr>
                  <th>Obecności (%)</th>
                  {
                    for((_, (attendance, questions)) <- statsByAggregatedPosition) yield
                      <td>{ show_attendance_stats(attendance) }</td>
                  }
                </tr>
                <tr>
                  <th>Ile próbek</th>
                  {
                    for((_, (attendance, questions)) <- statsByAggregatedPosition) yield
                      <td>{ attendance.sample_size }</td>
                  }
                </tr>
              </tbody>
            </table>
          </div>
          <div>
            <h2>Średni wynik dla wszystkich pytań wg stanowiska</h2>
            <table>
              <thead>
                <tr>
                  <th>X</th>
                    {
                      for((label, _) <- statsByPosition) yield
                        <th>{ label }</th>
                    }
                  </tr>
              </thead>
              <tbody>
                <tr>
                  <th>Pytania</th>
                  {
                    for((_, (attendance, questions)) <- statsByPosition) yield
                      <td>{ show_question_stats(questions) }</td>
                  }
                </tr>
                <tr>
                  <th>Obecności (%)</th>
                  {
                    for((_, (attendance, questions)) <- statsByPosition) yield
                      <td>{ show_attendance_stats(attendance) }</td>
                  }
                </tr>
                <tr>
                  <th>Ile próbek</th>
                  {
                    for((_, (attendance, questions)) <- statsByPosition) yield
                      <td>{ attendance.sample_size }</td>
                  }
                </tr>
              </tbody>
            </table>
          </div>
          <div class="center">
            <h2>Średni wynik dla wszystkich pytań wg typu zajęć</h2>
            <table>
              <thead>
                <tr>
                  <th>X</th>
                    {
                      for((label, _) <- statsByClassType) yield
                        <th>{ label }</th>
                    }
                  </tr>
              </thead>
              <tbody>
                <tr>
                  <th>Pytania</th>
                  {
                    for((_, (attendance, questions)) <- statsByClassType) yield
                      <td>{ show_question_stats(questions) }</td>
                  }
                </tr>
                <tr>
                  <th>Obecności (%)</th>
                  {
                    for((_, (attendance, questions)) <- statsByClassType) yield
                      <td>{ show_attendance_stats(attendance) }</td>
                  }
                </tr>
                <tr>
                  <th>Ile próbek</th>
                  {
                    for((_, (attendance, questions)) <- statsByClassType) yield
                      <td>{ attendance.sample_size }</td>
                  }
                </tr>
              </tbody>
            </table>
          </div>
          <div class="center">
            <h2>15 najlepszych wyników (osoba, przedmiot)</h2>
            { show_per_person_stats(statsByPersonSubject take 15) }
          </div>
          <div class="center">
            <h2>15 najgorszych wyników (osoba, przedmiot)</h2>
            { show_per_person_stats(statsByPersonSubject.reverse take 15) }
          </div>
          <div class="center">
            <h2>15 najbardziej kontrowersyjnych wyników (osoba, przedmiot)</h2>
            { show_per_person_stats(statsByPersonSubject.sortBy(-_._2._2.dev) take 15) }
          </div>
          <div class="center">
            <h2>15 najczęściej opuszczanych zajęć (osoba, przedmiot)</h2>
            { show_per_person_stats(statsByPersonSubject.sortBy(_._2._1.mean) take 15) }
          </div>
        </body>
      </html>
    fw.write(report.toString)
    fw.close()
  }
}
