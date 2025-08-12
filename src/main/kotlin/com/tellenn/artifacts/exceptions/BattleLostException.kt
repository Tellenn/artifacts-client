package com.tellenn.artifacts.exceptions

class BattleLostException(monsterCode: String) : RuntimeException("Battle failed against ${monsterCode}"){

}
