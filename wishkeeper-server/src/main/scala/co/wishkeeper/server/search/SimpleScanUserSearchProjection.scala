package co.wishkeeper.server.search

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server._

trait UserSearchProjection {
  def byName(userId: UUID, query: String): UserSearchResults

  def rebuild(): Unit
}

class SimpleScanUserSearchProjection(dataStore: DataStore) extends UserSearchProjection with EventProcessor {

  def rebuild(): Unit = {
    val eventInstances = dataStore.allUserEvents(classOf[UserNameSet], classOf[UserFirstNameSet], classOf[UserLastNameSet], classOf[UserPictureSet])
    val rows = eventInstances.foldLeft(Map.empty[UUID, UserNameSearchRow])((m, instance) => instance match {
      case UserEventInstance(userId, UserNameSet(_, name), _) =>
        m.updated(userId, m.get(userId).map(_.copy(name = name)).getOrElse(UserNameSearchRow(userId, name)))
      case UserEventInstance(userId, UserFirstNameSet(_, name), _) =>
        m.updated(userId, m.get(userId).map(_.copy(firstName = Option(name))).getOrElse(UserNameSearchRow(userId, name, firstName = Option(name))))
      case UserEventInstance(userId, UserLastNameSet(_, name), _) =>
        m.updated(userId, m.get(userId).map(_.copy(lastName = Option(name))).getOrElse(UserNameSearchRow(userId, name, lastName = Option(name))))
      case UserEventInstance(userId, UserPictureSet(_, link), _) =>
        m.updated(userId, m.get(userId).map(_.copy(picture = Option(link))).getOrElse(UserNameSearchRow(userId, "", picture = Option(link))))
      case _ => m
    }).values.toList
    dataStore.saveUserByName(rows)
  }

  def byName(userId: UUID, query: String): UserSearchResults = {
    val friends = User.replay(dataStore.userEvents(userId)).friends.current.toSet
    val searchTerms = query.toLowerCase().split("\\s")
    val matches = dataStore.userNames().foldLeft[List[(UserNameSearchRow, Int)]](Nil) {
      case (list, row) if searchTerms.forall(s => row.name.toLowerCase().contains(s)) => list :+ (row, calcRank(row, searchTerms))
      case (list, _) => list
    }
    UserSearchResults(matches.sortBy(-_._2).map {
      case (row, _) =>
        val resultUserId = row.userId
        UserSearchResult(resultUserId, row.name, row.picture, row.firstName, friends.contains(resultUserId))
    })
  }

  private def calcRank(row: UserNameSearchRow, terms: Array[String]): Int = terms.map(calcRank(row, _)).sum

  private def calcRank(row: UserNameSearchRow, query: String) = {
    val firstNameRank = row.firstName.map(_.toLowerCase().indexOf(query)).getOrElse(-1)
    val lastNameRank = row.lastName.map(_.toLowerCase().indexOf(query)).getOrElse(-1)
    if (firstNameRank >= 0)
      10000 - firstNameRank
    else if (lastNameRank >= 0)
      1000 - lastNameRank
    else
      500 - row.name.toLowerCase().indexOf(query)
  }

  override def process(event: Event, userId: UUID): List[(UUID, Event)] = {
    val rowToSave: Option[UserNameSearchRow] = event match {
      case UserNameSet(_, name) =>
        val user = User.replay(dataStore.userEvents(userId))
        Option(UserNameSearchRow(userId, name, user.userProfile.picture, user.userProfile.firstName, user.userProfile.lastName))
      case UserFirstNameSet(_, name) =>
        val user = User.replay(dataStore.userEvents(userId))
        val fullName = user.userProfile.name.getOrElse(name)
        Option(UserNameSearchRow(userId, fullName, user.userProfile.picture, Option(name), user.userProfile.lastName))
      case UserLastNameSet(_, name) =>
        val user = User.replay(dataStore.userEvents(userId))
        val fullName = user.userProfile.name.getOrElse(name)
        Option(UserNameSearchRow(userId, fullName, user.userProfile.picture, user.userProfile.firstName, Option(name)))
      case UserPictureSet(_, link) =>
        val user = User.replay(dataStore.userEvents(userId))
        Option(UserNameSearchRow(userId, user.userProfile.name.get, Option(link), user.userProfile.firstName, user.userProfile.lastName))
      case _ => None
    }
    rowToSave.foreach(dataStore.saveUserByName)
    Nil
  }
}
