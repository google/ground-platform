/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openforis.collectone.v2.core.forms.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Inline choice image payload (MIME type and base64-encoded image bytes). */
data class ChoiceImageMedia(val mimeType: String, val base64: String)

/**
 * Registry of choice option images (`jr://images/...`) for multiple-choice questions (`select1` and
 * `select`), providing built-in icons for example forms and runtime registration for user-authored
 * forms.
 */
object ChoiceImageRegistry {
  private const val MIME_PNG = "image/png"

  const val ICON_FOREST =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABrklEQVR42u2YyU7CUBiF+wIacemwwCEmLFWQuUyOr6E7jTX6AF05z9NKGXTFQp+hC4O4EXwGfJDfnCYQKKT3thfkmniSL2l7b//zJaULqiiWeKaH1Ym1KT1zv0K9BB3oUuwypwWMXotYQWdHmVnNb6TvlqkfoFsamTapoSmPmrpdIhmAi+JdndRlEYKLkrxZJLcUPp6p+v1F9eAY10RmuhLaed0jVrDHlVDiOkNO0F52iTfY63S+Y6HmR8QK9joWUq/SxEu+/EROg3ucdMgnFL9MES9u46SDWyhXLrgWwr3cQrGLJLGo1KokGszg6WIK5d4L1K1gFlMoep4gO7odVt+/0N8TipypZEe2lO+aDGax+phCgPXabxU1E9Zrz9OlhE/jxMNjKdex6LNWMdcAjjsFa7w93ELNYmCzuG2eW4NrWKvvczpfCZ3ESARrROfJJxQ8jpII1ojOk09o4ShCIlgjOk9Y6OEt24Kw0Gh6XA8chkkG4KIMeAdVWYTgYv6/n9nwGf6DEPUTOLR8AemnVJtMQ2rdZ8zvB+k3QaftVzQ8x5HUmN5rEXQ0fjNN+QHAZAUJ2dL1vQAAAABJRU5ErkJggg=="
  const val ICON_SAPLINGS =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABn0lEQVR42s3Yx04CURQG4BuNaBQsYN9rgonPwNqImogrfQldW2ZH8QHsBXtvKOBuEtEdiAvaE+iChzh6SJBxKN6ZM8A9yZ/csDj/l8tsZhhTjck+6rC45qTB0zBUM9iBXazS2JZ8crUh6mBnSYx10SsPnISgHsFuYTBFKPwf+4+DIEJyz5R5elYSBYQW1nf0BCJFPFDv4SOIFPFAPQcBECmGgF6/srnkZ/UjA1PPb/pA3f4HoMQXz0C5QaTWfYyKUd6MEShm278HLZkMR/5FqEfLfqYVo2d88TQ/yLp3B7zRO973NHcHN2giFOEqjnxmawPCpVpuAs9aIIaBlDeiB1AE6tq9BZ44gy8/hSlFearkb7z7yoVRFxgO6ty5AUo8sQIIz9R94oE6tq+BEk8sqQAlgbpPPFD71hVQ4o4WQHim7hMPZNm8BErc0YQClADqPjJoLCD/gvBMBpmcM5J54wJECFpY4/CIQxQQWnLv9y0LK3Lb+jnUM2j48wWknqgiTH6a55fl1rUzqGWws+JXtIYhu6Np3CVVG4Id2KXu/wZhbkuaEAkGwAAAAABJRU5ErkJggg=="
  const val ICON_AGROFORESTRY =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAAB9klEQVR42s2YyU4CQRCG+yk0kngg0aNe1IseSFwOGo3eNHHfWVTcr5wURRQUfAXXxA0FPHFQlDdA8AQ+SEmRQFimu2vCAFPJl/Skq+v/MsMcGMZKqrXZYBrq6XJc7tugmmAGZjFR2SfGItUWKQUzlWXGRyP+PSvUA8zWjUyZVEvmOfp2LaAH0IUNdnc69CKELuxixwxUYu+BItScpcKoIrzSWoydb6+CiL/fBMgKe2RzqAiFYuEAUAt7NRHybq0AD7UlmkWFK/QdflEtdO93Vy7k2VwGJb5D6oXwDG8eFf0JndmXQImv0LNqoTufC3jzqDDRptqqVCYrdLqxCDy+gvS7dHvhAtEsKkzWkE7+SGWwRwsZkpDsTvHuTOkZvNZMSCmEF4CClTxa5l5fACrR4FN+MK6VetLJuOTxxoUZmgrdnB+TXgDs4wqdrM0DlehbgVBmLdoXldLZHPoTctnmgMrn22N+KK5F+6K69jq5GZoKIbJKZX7Uogx2bJ0FKp+vBUKZtVLPlccpFMJ9UYbmQjmpVKL49cdrmUxW6MgyA1Q+Xh/yAbiW9RcWNUOVUC1gfZ3tDqd5GvQAujCjodGkFyF0yf6/XxoZiByuTkE9QYeiLyD1lCqTydXicH/kYGUSaglmCr+iGZsaTL0dbY5qi2AGZpXm/wNd+gegO0ShtwAAAABJRU5ErkJggg=="
  const val ICON_CROPLAND =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAACMklEQVR42s2YyzIDQRSG5wlUsWbFKlVUWVAuYdx5B1ZsEXasZs2apeszUO7G3QJJHkAoD4Byl+sxZ8pEd6e7Z/SIzKn6K53+u8//VffMItE0pvT6Ct0YqTHgfACKKczALE1W5nyjWWwQVpjJh5lrMOGsH0ohzA4MTAGUXl+uw2kfBEHIohnD1UZQgJBFy530gi/drYJTOPbbzxeQqHwBZY97QEW5uxUxkOWp9lUGcitloMxRN6jIrVT7KgPlHuPiK7M8ZaD0YReoKBOfFJ+O5an2VQZyoHKPMeJkYr5gbKDUQScEScpA6diELbc5R5nbZW9ASbMDfqtULPLzvNwuCeccZYlrdeutBEQ9wN/hZCiOResR/E+ByGAygAeJwjFZf3pC5LWQJyELJT325LhAn/s6eBVbvPn0zZJwD+vx5Bko+0BfVTIasefxkzfPA2I9LtDHXju4KXk1Tl/VQzTv4Zg3j0rfLFL7vGR5AmJL5CEA6ZGwrCcEet9tA5kyRFP7ubkcy3upBH0C7F7qbbTWumWhpEAYTr3OFpzXQHavFxgb6G0nDCKxRXofl6P0NVrfST+VWBDulUkIxDaUBeLJsfvJq8a1noFet1uBp+Q1EXgfLfBxLv86W2tl/vvFKIhyWGkyE4N4YSgM8eMLgaaHqoyXrRYIgpBFC9eV6UEBQhb79/36bMh83myGUgoZqH9ASglVAOPU2kzIfNpogv8UZkr/RQvXlulTg5VGsUEwA7PY/C84DClC5zrBpgAAAABJRU5ErkJggg=="
  const val ICON_PASTURE =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAAByElEQVR42tWYyU7CUBSG70oTEt2IGxUBZ30ESGj0JUjUlb6C4ATWae/wAPIKuhbtEl9CX8B5pAWTYw/Jba+F3pYO9vonX3LCbc//tTtKiCW5dEwqLwzKzcM5CBPswC7CS3U1qYQtYgU7O8pcrowqjYNZiALsFkamTSqXikna/gyIALqQ0nxcFkUIXYi6Nw0iIZ5QfXcK/KJVllsEsYsEIUMThBT5kifBDw3l1BDC2e8+rpBaWTLKcPYq5GaPIfS5MwF2NK5PzDJ9DvMaCleofrZoLPq+u/FchvfS4E6u0Ed5HHiw6XSuMUI4e9nB4ijEPp1fIdzlKPReGgMe2tWxWajPQZ9b+X9CiPnKa10X4j00brq6Euq01EmId29HobftNDjRZJ7SeqYyQjhbz2lwh5suV0K8Uq9ntkKvWylwQq0emYv1OYgzO1wJIcarv625LsVradz2kJfNJNhR1wsobNjf2VKc3dzD6+QKhRWu0PPGKNgRVnid4gk9rSfAjrDC6+QKRQEpZvvlx+IIiAC6kGyiVxJFCF1a/+8v8nHloTAMUYIOv76ARCnVJkNznh9Q7teG4C/BTu5XtEyiRypk+uSwRbADu6z9P/N8s3Sz4JyDAAAAAElFTkSuQmCC"
  const val ICON_WETLAND =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAAB6UlEQVR42s2YuU4CURSGb6UJiTZiowi4L50mJFqND0FEsdBEK23cdx2fQMFEFLXQx3ALJbjvioKlQo0PcMwhmQSGmTN3EOSe5Cu4nHP+j0tCwjCmKovTJVX3jssd629QTDADsxhVjuGjULFF1GCmpox96DDULr9CKcBsYWRypCwOl9S29gIigC7MKo3JogihC2tdfQaREE+oZeUJREI8oeblRxAJU0KDB59p8g3D2a3zJC3UtPQARvjPkqAuPOOZRbz78axZqpcZLYp8/gBV2EPt0JonhRoX70EP3sJQ9ax3L6bbT2VyCWEgBmSGaX1yPPOfJTTPM+dJoYaFO9ADy3ea0H1/IBgzvD1qXgtSCAN5loQ1bgvPeOe5hcyA4XgbSD4i3EJKSL4BZudZ/fwt6JH7NaSA6s/Ed/qd17wpIaX6gx+6M/geBlNFCjnnbkAPqsLxVE7/5sk31+8WlUkKeXbfs16jBG9hr3peESaFHLPXYAbPTtRQBnuoeWq/aSEFrdtK34pBoBHMPnMFf2Hj+CtNXyAKf92FsEIsKSSsbvoSREI8ocqeUdk2dQEigC6s3NYliSKELun/91Z3IFQ7GYFSgg5ZT0BKKZUjo1SVeztUMxGG/wQzyadoZbZOqaJ7RC62CGZgljr/F+ZCk5k/kA6aAAAAAElFTkSuQmCC"
  const val ICON_SETTLEMENT =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABaUlEQVR42s2YSU7DQBBF6yQhAY4BMpMYrsACJBBIIMGaMZ3EY0amA7BhwS0sIe4RBw5SpCI5Ig622902ri+9TatU/8ledQNEsri0bGzt7Alv8IJFQh3UBUk5Pb/0ixaJQp1/ypycXfhu/xnLgLrZyMxJ1cb/0ek9IQfIBTa3dwUXIXIBu/uInOAnZHUeUJdg9D0hj12Qh0yYPKTAbA9QlWD0hdHQmc5OyFMmDyloeX3MyjBBJgzNqOyGImR0pKDp9lCWLDK/pbJ0QJEyKlLQcLqYxOvbOw4DdZmp1HgH7UrrgzSZvJMmlfqFQnQj28NPSNgdlEE3sj3aQv7H5wzaQnWrjTLERXUuDn5C96aHMsRFdS4OfkJ3LRdliIvqXBz8hG6bDnKCn9Dq2oa4adjIAXKBSrVmcBEil8n9fv/w2L8WFpYJOcy8gJQpNSczlTo48q/qJv4n1Jn4ilZZqBorxrooWoQ6qCva/wM1OckGWAn0qQAAAABJRU5ErkJggg=="
  const val ICON_INTACT =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABlUlEQVR42s3YyU7CQBjA8XkBjXh0OaAYE44qyF4219fQkyKNvkBP7guuN6PxJIaH6MXEk8VXEB7k028OpJRpO5ApfJP8L81Mv19DL5QxxwotTGpz2xGj/LQJQYYzcBbzWst63Awa4gxnCjFLeswsPW7AKMLZZDA9qIlISCs+rAOF0MLCW/MGFRBaWOF+DShFD5S/KwOl6IG02xIEmd44gtevN57MfhY0xr6a7R9/UK5WhKCy2k1wLr8zbJiYauPQH5S9KYDqrFYvBq/JnGWUMByUuc6DTJUPXWqPCCM7A5MC2Z/aDaYCIw1yLidKhPHCKwfZh6nEcFD6SgO/KnWXof/XRS8xXpe5rygmu9ENpRLDQanLHMi2X696Yr5bFvRzP1Gs3wNuKBUYDkpeZKHf9t4PujG/FgxyH1Fs0IOIev584anCcFDiPAOUogdaPUsDpeiBpkuzRvw0BRRCCxsLj2tUQGjh/+8Xd6Nm7CQJowwNXV9ARonqwXRQO1Fz5TgBwwxnen5Fw99xqjhjBA3BGZ13xrb+ANfMnqjB7tDQAAAAAElFTkSuQmCC"
  const val ICON_LOGGING =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABT0lEQVR42u2Yu07CYBiG/6vQQOJAoqMuwqJDEw+DRqObJIqKnEHF89pJUMAT1ZvQCRbGbnALxtEbefUzcbCQ+rWl9ht8k2dq/z5P0qlVyrKJsbC2PBvTny5K8BNykEvZrRxfN/0OsULOwTEba+bjeRFBQG4xMX1R45/v0TgrQALUopZmorqUIGpRzdM8JCEv6OEkB0nIC7o/zkIS8oLujjKQhOugF6OOXqc9ELrmOui2nIYTup0WuKN7nT6fHfRs1OB2dJYddHOYAof3t1fXQXSW62EHed1/0NCCGgd74OB1XI+8oPp+Ehy8juuRF1Qr7YKD13E9yu8Qp2HqurgDO4a933zygq4K25CEvKD56JRezScgAWpRkfCoJiWIWr6+79Ori2Ylt4UgoYYff0CCjOqL+V5qZcG8zG7iLyGn7V+0SGhEm5ue1P0OIQe5rP4P38b21VZjYJgAAAAASUVORK5CYII="
  const val ICON_FIRE =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABpUlEQVR42s2YyU7CUBiF71ojbhwWKkZ3osyzQ3gWDWiMvkOfQgbd+SKsBZxHjHHntFX3vzlNINCWcgdq70m+pMN/7/mSNiSUMUsygcnC4XzQaOe3yEvQgS7mltNQuO61iBV0DpR5ym2SH9ik/JSxSaUnAoXH7AbpAFzYwdyCoYsQXNhDZp10Qj+h+3SedEI/obtUjnRCWeh1d5++aicmOFYWuk1mSRYIWINrKnsyFZnf1rlNCNdUpNhNIkOi/DRbNCyYkdmbeSGjIsWu42niRUSmV0qkg1vopbhHssHakQt9Vo+lhbB25EKq4Ra6iqWIB7d8VGombuHtURLCC2udG/TicwtdRpPEg1PRoFkncd4ebiGnR8IrhLXcQheRBPFijeqcE0JCzzulviKcy8y4Cp2H4yTCd6PZV/hernbv4bg3mBXdX1gItLeLQ393MCOzN2utxUiWt6OKSSedc5U9lYS8gJWmZ43mapR0AC4sMTZe0EUILub/+8ricr0RipCfwKHvC4ifUjaZTsrBpfrZSpj+E3S6fkXDcyxOzRhei6Cj+8705A8mePEK0+lGdQAAAABJRU5ErkJggg=="
  const val ICON_GRAZING =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABrElEQVR42tWYyU7CUBSG70oTEt2IG5XJWR8BEhp9CSK60lcQnMA67R3WKq+Aa9Eu5SnwAZwnKAVz5JjcpFC43KsWjn/yJbQ99/5f024oYw2Jhjxaen5QrxzMgptgB3YxUXIrAcNtkUaws6nM5bLfsPZnoBtgNxkZh1Q06NHKe9NAAXRhqTmvTkUIXZi5OwWUoCdU2pkESigLfd7mgaecWXJcx3M8OKssVNQnQBYzswj2WMaJYwbP2YNrVDqUhBBVIdX92cf2OKhQOo9DtZAH6/q45QxewxmcVd1fWcht2Ht6DGQpnsWhXLt7Tqs5+wyuUemQFsJH0Cz2QvzdLLhWWugtNQrtKJ4ugCh8ThTcQ6ZLSqhauPm1EO7xZ0Ltojr3v4Ret0LQDvPqSFjE50TBPWS6pIREZZXau8FnKoJ3TbaHvWwGQRYzd1h/17Xjn8yIUBLqBOx5IwCUoCf0tO4HStATelzzASXoCSUj/fpDcgQogC4s4uvVqAihy/f/+4uY17hPDEM3QYe6LyDdlHLI8GRjA8bd6hB0EuwUfkUL+3q0RLhPd1sEO7Crsf8LWi40pmBsmF8AAAAASUVORK5CYII="
  const val ICON_INVASIVE =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABuUlEQVR42uWYuU7DQBCGpwOJAipCASIkJBDOGppADRUIangEwg0JBp6AU9z39Q7hcAuIsw8pgbcYMitFIo6zu15niSVG+gpHs///RXZjA1jGXxGJ9voGjeXOK9QJdVAX8GY0mDB1i1ihTluZkUDcXOq4xFJA3Z6RyZOi+7jYfoFegD1TPdUDhleEyAWMtnP0EkIhc/Od4bZINgcWWs+wEHeZgOykH7+Rt8uDzmaHMnm70kKqUr9lpIQSLafII/3wlSuVuRadcXMWdAWr/hGIR05QBruCYuxakRbiFd1uvDHcyjCh+eZjdIK1kDe06zTfsZCslIoME5prOkIVRKOaqyR0s/4qFKKd/yuk9ZbNhg/RKR/3n0Ih2lHJBh0ybqRgJnSAslhl6Jp+v157YfB2ZAG3Mm5384SmG/dRhF2BjjME6ApWPQtTwT0sRHL1OScwlQnk7duRskhRJm9fWkhFxk5KKDQZ2EUeyZUnhmivWDngtqjYQHdVvzHRsINegFygrjwc9YoQubD3++GamDnu38ZSQg45X0BKKZUnk50h35gZq9/Cv4Q6uV/RastC0a7KPkO3CHVQl7X/B5RvJ//ZnKpXAAAAAElFTkSuQmCC"
  const val ICON_COCOA =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABvklEQVR42t2YyUoDQRCG+ykUAx4EPerFeNFDwOWgKHpTcDcxqxr365w0LomJmryCHhRcwOU2F5dHMMvJ5EFKS2hNZtrY1SFJ4w8/zFRV1/9Bn2YYs6it2eEa6ukyUtshqKYxA7NYOYUnxsxqg1iNmWKY8VEzuRWEehiztYGxQbV+3uPpZgB0MLKwwW6noQsQsrCTDT/oZP2Ajtd9oOrLVAwKucz3Oz5jrZKdykAYzsVrXMWQZKDEmheofn28hWLxerFwRmU3GegiGQWrREAonCUDxVcXgeJCLm0L5j2rcJa6nwwkkkxPGugo7AFZvzzcCEN5XyQ8Q8nQDyi24gZZ57NpYSjvi4RnKBkkoN8k2///QDW5sujyAsj6+f5aGMr7IuEZSoZ+QIdL80CxSDI9WZOB8tk3aSCcJQMdhOaA4vNExBbMe1bhLHU/GQj9dHf1JxDOqOxm+8FZUPF75ufqeI0Le6p7lYHQZ/FISTg+Y62SnWwvMAM6WT+gPmeHEfFPgw5GFtbiaHTpAoQsX9/3npEBc9c3BfU0MpT8AaknlA2Gyz3cb+54J6GWxsyyf9FamhpcvZ3tRrVBMAOzrPkfjs+XQSzatGsAAAAASUVORK5CYII="
  const val ICON_COFFEE =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABxElEQVR42s2YyU4CQRiE+6wRLy4HFaM3UfbdJTyLEXB9h3kLNj35IpwVcN+IUW9uR/X+myJpMjBM0z0j0pXUgf5rur5M94VhrEsJz3jmYNZrNNMbNEijA11MpGOfvzpokG6j0xbmIbVOw7AFapgwFqj4mCdzn1wjHQwWtj8zZ+gCBBZ2l1glnawf0G08TX/hl+29lt3uw9wAQPz3Z+WIuNyAsZtYilT9U2+0y/lat5Bxsrcy0Ef5sKPYDghCVhnoOpokWT/ndy2lfGYnPKPSoQRkPipZIDyjBHQVSZCse0k0687IWBrovVTpWfaU2xHOIcykgS7DcZKxXSHWRXNzRsaugb5r9XZGCyCIZwCnBVC/Y1MCugjFSMZvxbItEGbIPG7lhXMZSwPBIokyKh3sPBglWdvdEYhnel16lQ4lILsjEQHhGSWgs0CEVPxaKPUE4nOzkFXdXxkI/jqt9QVCxsnerOEPkxM3N7Mdb4KvQ5g53dcxkBnMDYAFqL4SIp2sH1BuctqoLQdJB4OFRUZGM7oAgaX1/744v1g99QVomAZDxxeQYUJZYLgK3oXqyZKf/tPoFH5FwzlmJ6aMQYOgo31nTPoFKfQILmGJTWMAAAAASUVORK5CYII="
  const val ICON_OIL_PALM =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABvklEQVR42s2Yy07CQBiFZ6UJiW7EjcrNuz4CJDT6EkTd6SsI3sB6W3vZG30FXYN2CU+hD+D9Rlsk+e3fpOlYpkPLiMxJTvKHmTnno+2mJcSjbCqilBaH1e+jeeimsQO7CE+VtYTWbRCvsZMJU16Na43DOeiFsVsamBaobDKimAezIIORhRQXoqosQMhCjP0ZkMnyAel70yCT5QOqq1PgZ+NyBVDN+5o98/a2y8EMVLscLpBXYcFoEFpcoK/dSfCzfrEMLDXvavaas69xe2abPod7WKLPscwFogtFRQNzgT5LExDUZgdgeCZMRyigsFBhYWygj+I4BHH9fMl6LqqM56nKnOnf8GzQnkBAfkW4Zt6culfEmtvtFwaiC1n/mAXkd0Xp9Y6BMJgX6AfEWg9y68j7TgpEbFCFOIvmyQf0tp0EERuVExfImkXz5AN63UqAiHUKCGfRPPmAXjbjIGK9fOwCWbNonnxAzxsxEHGdAsJZNE8YyIH6CxgbqJAZVJ8KYyCDkYVkYv2KLEDIYr/fX+ei2mN+FHppZPj1BaSXUC0wjq5yQ9rD+gj8p7GT+xUtHetT8ukBtdsg2IFd3v4fzkY/KG66AvwAAAAASUVORK5CYII="
  const val ICON_RUBBER =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABlklEQVR42s2YyU7CUBSGbzSiUXAA570mmPgMfQDQhbjSl9C1Q5fiCzjP4jwrw7LRupWlvgEm8hDHHBKS24Hmnmrp+ZMvIbflP19O2VAhbIkkJ7VYZl4fPS9BkOAMnCW8klheN4IWsYMzXWXiS1lj5KwIYYCz2cg4pPA5DucKwIHabyo6M6dzEUIXMXSaB07wExo8eQEKKqF2yvATGjh+BgoqoXbK8BPqP3oCCiqhdsoIP196/64GJ5Q4fAQ/TJfMhkJ+OxHhR0T+7LatPwnFDx6AAmaqaFrOsuUvixC1U8aXEMasVC3nKIlnoQnVY9+WXbTpQm7bkh8jXrNLewr17d8DhUYxKz+QLrzV7smWPx3XVfv/TagelEE5t3Mlod69O6CgkrUP54ZU+wMRwvvkLaXyr+pCPbu3QEEl1E4ZfkLdOzdAQSXUThl+QrHta+AEP6FIelaPbl0BB9BFtI5PaFyE0KX2/75jcdXo2ryEMEEHyxuQMKUcMvW0L6wYnRsX0ExwpudbtJaxpNaWyuhBi+AMnGWf/wsAx5g4Gj6FNQAAAABJRU5ErkJggg=="
  const val ICON_SOY =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABoElEQVR42s2Yy07CQBSG5wlIdK0rXZFowkLjBR3v+g660rXXla661rVuqb6Dxrv17kYFH0DwCSTeuRSOHhJMKKWcKS2cP/lSSGbm/5ihi1YIS2SoWWpz7RrcTYGfYAd2CacYW92G3yJWsNNeZrPLgNtJaATYzUamTEqGmiTcTAAH0EVos20aFyF0EfnrceAEP6Hc1Rhwgp+QeTkKnvO0AvlkDDB4xe/UuZ4L5V52wC5UKZG9GAEvqZR8Mkqa74mQGVsu4CSEIQllzofBLdnoEqiEsqaol4yZ2KYJpY0hcIOZ0Mkyub//D3VdoSpRBEsq74b+P171h5KEMtFFxePRwe3Ok4ScdqOW47EVSp1JqIbjrRzXSz5T1nOiZqFaBcqEfk4HoRrZeMT+eF4fgTJfBUEdiOXWpB8WvBf6PhkAKqn7ecg8RwqozFNB+LWwa6Gv4zBwgp/Q51E/cIKf0NpMq/Zx2AccQBcR7gxILkLoUni+39sIGu8HvdBI0KHkDUgjpcpkitldDxpv+z1QT7DT8S1auCMgV6dbNL9FsAO7rP2/HjveVOI/FTYAAAAASUVORK5CYII="
  const val ICON_TIMBER =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABrUlEQVR42s2Yy07CQBSG5wU04tLLAsWYsFRB7oUWr6+hGyNawwt05f1+20FxZby9QzeCK8FXUB/k6CGRYGtnpgOVOcmXNJ2/839pu2kJsU1gYlAZWw4Z+etF8BPswC5Cm2k9avktYgc7/5SZ0iOWdrUAvQC7pZFxSA2EAop6OQ8ygC4kuDRuyCKELiR3MQcyIZ9Q9jwPMtGRkP5UhMbnG/wMHuO5joSUMw1E0B+L4Da4Jrov6bZMp1Ikc6qCV8yXG6YQZkT2lk8ofZIDr5g1DqHvjMjeQkIbD1tMIcwICaWOsyBC/aPhKoNrovtyCxXudUcpTah98NquCtHKsaxcqzSxS4vcNeL1zrhJ8eaYQskjBWiUqxVmEWZ4c6w++YQShxmgUaqazCLM8OZYfUyh9btNZhFmeHNMofhBGli8vtddS3DNa44GlxBSenY+EjwnmnMViu2nwAtrt4Um3crZ8SzkN2R2LwkyIZ/QsDZqRHcTIAPoQvqC/YosQujS/L6fXA1bkZ049BJ0+PUHpJdSDpmW1ErYmtmOwX+CndS/aPgch9QRw28R7Gi9M23zBYI8bkxunFccAAAAAElFTkSuQmCC"
  const val ICON_BEEKEEPING =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABn0lEQVR42s2YyU4CQRCG+6QJiV7Ei8rmro8ACRN9CWL0pK8guIHjdnc5m/AKegad4/gS4gO4b8AISZki0Ywz9ExPt0BV8iWd7kr93yyXGcYclU6EtMLisN44modOghmYxbyqvBYzOi3iBDPbypRWo8bX4Rz0AswmI+OSSsdDmnUwCxRAF5ZfCOtUhNCF1fdngBL0hGp706BKwzhr8R+zlISs4go4C/eUhKr6FMhQLy4Dr/BMdq60UPPuhiuEZ9JCn7uTIINfyc6lJ/RRmAAZrOtTrgyeyc6VFkKaFfd7hHsqM9l7fhxUqJ4vgXV10gLXqvNYkOBmxbTdCbOtgGifkhAO5JU9TLRPWch+xe53xgzc5yn0tpMAP/wqaJ8X9IRet+PgR+OW/yjq5ePfPlzzCmeIZAkJIaIhPHnRHPayFQNRarY7gGvVvnYEEuoG7HkzCpSgJ/S0EQFK0BPKpQb1x9wYUABdWCrSr1ERQpfW9/1lJmw8ZEehl6DDnz8gvZRyyfzURWbIuF8fgW6CmZ5/0ZKRPi2bHNA7LYIZmOXM/wbHNEYkDKCEbQAAAABJRU5ErkJggg=="
  const val ICON_COOKSTOVE =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABV0lEQVR42u2YOU7DUBRF/wZAhJKhCARFSgkkZP6JHcZtQIuM2IAr5nlqCbSIPbiECvbARi56LiL4diLHD+OHxJWOZH09vXsku/FXykhmblzPbOTczt0akoQ6qEsNyoJT8pIWMaHOUJl5p+jZt6tIA+oWIxOQGstltHWzAgmQi8quz7pShMhFta+XIQl5Qq2rDjg4z7vovj760DN3H0uIJMzQGUtIX9qIS79wdsoTal5YiEu/cHayhO5fHgIydMYSapy3wWH7aQdvH+8+9Mzdxxb6aVT9rIWoxM0wHf9Cf0+odqoRlbgZpoMlxJ0LFaqeNBEVM9y5MOQJVY4biIoZ7lwY8oTKR3VExQx3Lgx5QkuHNUhCntCkPe2WDqqQALmokeyoliJELv7/fX6r4BX3K0gTcvh2A5KmVECmJ7VZ8Bb3yvhNqHPgLRq9xwlryk1ahDp638yXfAKxChHWcqXa+QAAAABJRU5ErkJggg=="
  const val ICON_CLEAN_GAS =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABfklEQVR42s3YuUrEUBQG4FspDGjj2GicjPvSKQxoFR9iGqdR0Eob913jE4gWCmqjjyEKKWdcZty1GC1Fex/gyAlciFlPyMScH/4m3Mv/zXSJELaksjmteXRGH9h5gziLG7gl/KJOnBlxQ+zFTVdMZvzU6NdfIYniNhuMA5VSc1rf9gtwKFpEWpvWuYDQInq3noFT+YF6Np+AU/mBujcegVP5gbrWH4BTQ4EKJ+9Q+vgBmf3Lb8cZfCaDZ/FOKFDn2j1QWjiugltwVJ6xYq3Bu9QdMshrDCPPeMWKDgR1rN4BpX7Zu/gy6xfqDhlU9PmHgtB4t+agsaNq4JgXGu/WHOSGcvvldlQYjAlqX6kAp/IDZZfLwKlkUNSQQerSLVAaNdQdfqDM4g1QGjXUHTJo9/wzUsmgtoVr4FR+oMaRKV2ZvwIORYuoV4Y0LiC0mO/36fyh0TpXgiSLhj9fQJJEOTAyTfkDo2W2CP9Z3PT9ilanDGoNw5N63BDcwC37/i9cpKL7ytLFnQAAAABJRU5ErkJggg=="
  const val ICON_THREE_STONE =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAAB1UlEQVR42t2YyU7CUBSG70oTEt2IG5XJWR8BEhp9B8XoTl9BcALrtHbaGY2+gq5Bu5On0BdwZrAFTI49TUpq29vCrZUbT/KFpufe83+lJA0lxFTJWEDIzfaLjYNp8BPMwCziVIWViOS3iBnMtJXJL4el+v4UdALM5kbGIpWMBoTa3iTwALqQ7ExQ5EUIXYiyOwE8wZ+QvDMOPMGf0Kc4BjzhSUi5WoK6dAp64TGe8yRU3R4FFuTLRaAV9ljnMgu5FbNQJTcC7VK7O3EVwjUss/kTKmeHoV1qt8fuQuoaltn/Qwj5eixSZbDHOpdZiCblRUYTKm3FwAvViwVQ1NuD4LHXeaTVwIZ65fjZboBxbyvC5GMzCjSUwpHlljQeipZ1lfOUhvk8rjUXznTKdBSilVHEXEYxt/22Qu8bEaBBfVapV+nUx55s8+0a+zSYhH6rbyv0th4GGuWzeZvf0H2zTyu9j2vNhTOdMh2FdCk5f9jE2LMLNAojxr1uMprQ61oIWCmdzUHdIIXHeM7LTE9CfkAyiV7xJTMEPIAuJBHqFngRQhft//1NKig9pwehk6DDjzcgnZSyyOh1neqTnlYH4C/BTMe3aPFQl5CO94h+i2AGZpnzvwE+6WBeFRTB3QAAAABJRU5ErkJggg=="
  const val ICON_CERT_RA =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAAB90lEQVR42t2YSU7DMBSGfQEQsGRYlEFILIEyFQi0zNeADaIQ4AJdMc/Tio4bVIY7dAMtGyhXAA7y0B+pxU6bxAmEWjzpl+z3nv1/dtJFw5gpGruatPaFztjU1Rz5KXjAi9lFnz6Q8xvELHhWhenVg7nI5SzVQvBWBqYCqqGzUQtfzJAKAgsLzHfEVAECC5s8nyaVpB7QxNkU/UT6wyalnjOGMP7pfswrRPHzjawCNa9wTDuNkBulChmSDfS63d8VULVbQQ7GkFXdFdD4SZhklCykBSPMf6PXLCmgtfsNwQBzP9YYQGPHk+Sk14/i92nz6XJ+9W5dqGGMXKmOXr4m4yUFxAcPYxU8VLW1tkCjRxNkp+itLpyylOdvxhxWfdjLyc8RKJFPlTfEuJR3Cqf1/wfozx9Z6FAjJ/FRykWzuiUQanZr7SQFxJ8y8ZQSoMw/ex4GvXxNCmjkYJyctJJdE06KuR9rICkgKP6UFAww/43eCqDh/TGS1cv7a8X7glz8MWnIqu7GwxUQBGPZQK/b/dnQ3ii51fJNtOpt8LeCHi97ewIyw10/Jgx5hRCABndDpJLUA2qJtMUGdkZIBYGF1QXqNVWAwGL8v+9e6skFt4eplgKD8AWkllAVMGWoxZ5c/9YQ/aXgafsVDc+xOdwa8xsEHuV3hosv1OlbzlH9mtwAAAAASUVORK5CYII="
  const val ICON_CERT_ORGANIC =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABdUlEQVR42s2YyU7CUBSGbzSiUXAA570mmPgMPIDoQlzpS+jaoUvbF3CexXkegGUTWQs78G2O+ZvUSKHX3FsL50++3Da5Pf+X9G5aITyJJKdTscySMX5ZoDBBB7qELIk1yw5bxAs6G8rEV0177CJPrQDdbGTqpPAeR7M54oBzpqLziwYXIbiIkfN34gQ/oeGzN+IEP6Gh01fiBD+hwZMXCopV/nJA3GvdWSKoiCxaQonjZ9JlrlCUClnlqvJMLSEUQeYvKS2h+NETqeJmNl907rH6RXW2spBZqtYUyqSwt+lCMqmmCPmlkZTOcRADh4+kglmq+Eqlcx/OHqzYpzob/KvQbyldRP/BA6kiy+ZnhXRmugjdB1EMXImgIj9Cffv3xAl+Qr17d8QJfkKx3VviBD+hSHrBiO7cEAfgItonp1JchODifN93rWzYPdvX1ErgUPMHpJVSdTJuOpfX7e6tK2om6JT+RWubSKY6ZjJG2CLoQJe3/xuxJQkhFwCqPQAAAABJRU5ErkJggg=="
  const val ICON_CERT_FAIRTRADE =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAAB6UlEQVR42t2YOUsDURDHX6UQ0EZtNBrvq1MQtFo/hHiDglbaeN+6fgIv8E6hH8MjpDPxvu8kXTxatR+ZwEp24+6btxrzcODHPtiZ+f/YbZJlzFCO3Colo7ZbLZ++hXiCGZjFrMrVvumNt4gRzPxWJqdtw1um3kAiwGxpZGKkHK4qpXTqGmQAXVi60qXKIoQurGTyCmRCPqHiiUv4Ca3uECx4XiPg+af7mF0Jf+gDzArv2ZVjReMXIMKC5wWohb2i+4WE/KF303Ctp8Ud1PXhWUiocOwcKMzvWj8ZY3/zeuDrHs5Sc0hC0cupQsY5PJOECkbPgIc/+M4V4s3ilZJFEqKU2Wzz2iO3RyeUP3IKVjStPpKErHZohbt4eVyhuZ3nXxPCXf9P6M9fWd7wCfCglNls4+oDtycakpAv+GZbSJvFK0kod+gYeDSs3HOFeHN4pmSRhJDZ7SchoWgZnKXmMNfgEVDxBcxfndbTsHyn68OzSIaQEDK7HSb//MBe0f0sZ+AQRKlfuoM9i6eF97DHzm5bQka5ma1wBLsSOqHs/gOQCfmEUms6VWffPsgAurBkZ6UiixC6RP7fp9ctebN6/ZBI0EH3BSSRUjEyWqXVLXoze3zwl2Cm5Ve0JGeFklLdocZbBDMwy5j/CXpR5J15QhhkAAAAAElFTkSuQmCC"
  const val ICON_CERT_CARBON =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAAB1ElEQVR42u2YuU7DQBCGtwOJAipCASIkJBDOGppADRUICs7wCIQbEgy8AZfIzQ0vwBkS3ELB9QBQwmMMHktB8drEu5aMt2CkT7K9O/6/jCNFMSFUuSsCwV7XoLTReQ52ghmYRUrVlDcq2y1Cg5mGMiFPRF7vOAMnwGxhZHRS+BzX2k9BBNTvVE/1gCSKELoQqe0EREI8odXWYxAJy0KZUBbud9+ALryGa5aFoi1HwEN68hY+Hr/ArHAP7uW9P+GV4S1eKRIJHAILqYkbwynkd17VNQSPjaaHa6w5zEJ0UKkQWh57mYVWmg/AjNT4tVZGObejB2ESym+/aD4tS49VyHLTPphRXDlFjqXHKtxCSWX0/0KOPrIlfwbMyG09/wi9P3wCS49VmIQSY1eaKeG5HT2q0KIvDSzgZIorPnr5615cKy7sZc1hFqJDCkF3yuPENQSPaXEzeZ3QQmMKWImPXHD/uGIPTwaXUEHKaApG0+OVUYXmvUmwQkwJy24+6UTwGq5Zva9lIbsgc54EiIR4Qt1V/dJsQxxEAF1IXbk/KIoQuqj/74drwvKMOwZOgg6aNyBOSulkCjXkmpbD9Xvwl2BmybdotWW+YFdln2S3CGZgFp3/DboHyFrevlN4AAAAAElFTkSuQmCC"
  const val ICON_MOISTURE_DRY =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABZ0lEQVR42s3YSU4CURCA4bfShEQ34kZtBmc9AiR09BLErV5BcAKf09oY1x5C12jellPoAZwnkMGUFkkvaEg/ul43XZX8G+hUfQkrWgjX5NIxu7w+KVtnqxBmeANvCa+53UqqsCHu8GZfTGUzoZqnKxBFeJsNpgeVS8XsxskycAgtorQWl1xAaBE/x0vAKX6g+tEimNZSl52C2GUE+n2ognvwMyNQTS4ApXYfjDP4HXUvCdT8/3l0g8+QQN+H8+C3QYeymx/oqzwHfht0KLtJoPZ9VYvBZ0igz9Is+K12taEF4TOU3SSQDkXFGIGcGncXXZnuMwYFnfg4SAOn+IHe91PAKX6gt70keBX06O7xA73uJsCreuU80HT3tKBhJ152LOAUP1AxOy6fizPAIbSIrDVqcwGhpfP//iYfV0+FaYgyNHS9AYkS1YNx5jo/oR63p2CY4U3Pt2gZa8QuZMZk2BC8gbfc9/8ATG0qFtS4NDMAAAAASUVORK5CYII="
  const val ICON_MOISTURE_MOIST =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABg0lEQVR42s3YyU7CUBQG4PsCGnHpsEAxJixVkLnQgtNr6NKkj9CV8zywkhZc8hTdMOz0HXyRo6eJDbQCvfe09p7kT0h7cs6X3m4oY55KbCwqa0cpo948gCiDO3AXm1bbetaOGuIN7vwTs6VnbO11H+II7pYG40MtpBKK+rIHMgQtLHm4bsgCQgurPTdApsgHqj7VQabIB1IeNaCmPXx3EsasUEC/FQqo8qACJdaw44LwN3UeGeQtMqh8XwPRWIOOD4TXKDNJoElFApXuqiCSj6/PiSC8JzqXhY2horhBp10dghb2Rg4yB+3AIOzlBhVvFeBJkOMaPTbe+dwgs8/xhH56uUGFmwrwpNW3AoOwl3c+NwgTtERms/x1GXjT6s1+StgjMlsINAslinFAuasSUPLWM8dCnUcGhR22e1kEmSIfaFlbNbIXBZAhaGFzyXlFFhBanP/3mydpO3OehziDhrEvIHGifBgXdZy2d85y8J/BnVO/ouE5LqkrRtQQ3OG+MyP1DasVyomSXPwvAAAAAElFTkSuQmCC"
  const val ICON_MOISTURE_WET =
    "iVBORw0KGgoAAAANSUhEUgAAACQAAAAkCAYAAADhAJiYAAABlElEQVR42s2YyUrDUBSG70qhoBvrRmNb52GnUNBVfIgupBsFXenGedb4BA4LBXWjj+EAWabOs4LaZe1eH+DIKbSkick9wk1zfvgg3IH/uzerRAhHIomkXj84YfRsvEGQYAd2Cb/ER47NoEWcYOefMrHhI7PbeIUwwG42Mi6pSDypd62/AAfQRUT1cYOLELqIzrVn4AQ/oY7VJ+AEP6H2lUfgBD+htuUHUEn68BMy2R8oBp9xjLpfqJbxClVKtC7dA4X0wUfZyXfO86419nlncI7SI6gyXrGvk4Uk1LJ4BzIsn5Nvn+VL62ShdJGEqEUoRxGvmJDXbeIYpYcsRH1l9pvCPQj1ZkpCzQu3IGNo/91TiLL/PwjqQpSyst+2m/lSLlMQSszfACf4CcXnrsGPrdOcUmR9UiHVkQrFZq/AD9WR9UmFNk9ySpEKNc1cAif4CdUOjBna9AVwAF1EtdancxFCl8L3fTS1ZzZOZSBM0KHsD0iYUi6ZYupSu2bDpAWVBDt9/6JVab16Tf+oEbQIdmCXs/8XkKOsQA7GfF0AAAAASUVORK5CYII="

  private val builtInByFileName: Map<String, ChoiceImageMedia> =
    mapOf(
      // Form 1: Single Point & Land Use Observation (/data/land_use)
      "land_use-primary_forest.png" to ChoiceImageMedia(MIME_PNG, ICON_FOREST),
      "land_use-secondary_forest.png" to ChoiceImageMedia(MIME_PNG, ICON_SAPLINGS),
      "land_use-agroforestry_shade.png" to ChoiceImageMedia(MIME_PNG, ICON_AGROFORESTRY),
      "land_use-annual_cropland.png" to ChoiceImageMedia(MIME_PNG, ICON_CROPLAND),
      "land_use-pasture_grassland.png" to ChoiceImageMedia(MIME_PNG, ICON_PASTURE),
      "land_use-wetland_riparian.png" to ChoiceImageMedia(MIME_PNG, ICON_WETLAND),
      "land_use-settlement.png" to ChoiceImageMedia(MIME_PNG, ICON_SETTLEMENT),
      // Form 2: Sample Plot Forest Assessment (/data/disturbance_indicators)
      "disturbance_indicators-none.png" to ChoiceImageMedia(MIME_PNG, ICON_INTACT),
      "disturbance_indicators-selective_logging.png" to ChoiceImageMedia(MIME_PNG, ICON_LOGGING),
      "disturbance_indicators-fire_charcoal.png" to ChoiceImageMedia(MIME_PNG, ICON_FIRE),
      "disturbance_indicators-livestock_grazing.png" to ChoiceImageMedia(MIME_PNG, ICON_GRAZING),
      "disturbance_indicators-invasive_species.png" to ChoiceImageMedia(MIME_PNG, ICON_INVASIVE),
      // Form 3: Commodity Perimeter & Center (/data/commodity_type)
      "commodity_type-cocoa.png" to ChoiceImageMedia(MIME_PNG, ICON_COCOA),
      "commodity_type-coffee.png" to ChoiceImageMedia(MIME_PNG, ICON_COFFEE),
      "commodity_type-oil_palm.png" to ChoiceImageMedia(MIME_PNG, ICON_OIL_PALM),
      "commodity_type-rubber.png" to ChoiceImageMedia(MIME_PNG, ICON_RUBBER),
      "commodity_type-soy.png" to ChoiceImageMedia(MIME_PNG, ICON_SOY),
      "commodity_type-cattle.png" to ChoiceImageMedia(MIME_PNG, ICON_GRAZING),
      "commodity_type-timber.png" to ChoiceImageMedia(MIME_PNG, ICON_TIMBER),
      // Form 4: Household Panel Survey (/data/primary_livelihood_sources &
      // /data/clean_cooking_energy)
      "primary_livelihood_sources-agroforestry_cocoa.png" to
        ChoiceImageMedia(MIME_PNG, ICON_AGROFORESTRY),
      "primary_livelihood_sources-annual_food_crops.png" to
        ChoiceImageMedia(MIME_PNG, ICON_CROPLAND),
      "primary_livelihood_sources-beekeeping.png" to ChoiceImageMedia(MIME_PNG, ICON_BEEKEEPING),
      "primary_livelihood_sources-tree_nursery_pes.png" to
        ChoiceImageMedia(MIME_PNG, ICON_SAPLINGS),
      "primary_livelihood_sources-off_farm_wage.png" to ChoiceImageMedia(MIME_PNG, ICON_SETTLEMENT),
      "clean_cooking_energy-improved_cookstove.png" to ChoiceImageMedia(MIME_PNG, ICON_COOKSTOVE),
      "clean_cooking_energy-lpg_biogas.png" to ChoiceImageMedia(MIME_PNG, ICON_CLEAN_GAS),
      "clean_cooking_energy-three_stone_fire.png" to ChoiceImageMedia(MIME_PNG, ICON_THREE_STONE),
      // Form 5: All Form Field Types Showcase (/data/certification_schemes &
      // /data/site_conditions_group/soil_moisture_class)
      "certification_schemes-rainforest_alliance.png" to ChoiceImageMedia(MIME_PNG, ICON_CERT_RA),
      "certification_schemes-organic_eu.png" to ChoiceImageMedia(MIME_PNG, ICON_CERT_ORGANIC),
      "certification_schemes-fairtrade.png" to ChoiceImageMedia(MIME_PNG, ICON_CERT_FAIRTRADE),
      "certification_schemes-carbon_verified.png" to ChoiceImageMedia(MIME_PNG, ICON_CERT_CARBON),
      "soil_moisture_class-dry.png" to ChoiceImageMedia(MIME_PNG, ICON_MOISTURE_DRY),
      "soil_moisture_class-moist_well_drained.png" to
        ChoiceImageMedia(MIME_PNG, ICON_MOISTURE_MOIST),
      "soil_moisture_class-saturated.png" to ChoiceImageMedia(MIME_PNG, ICON_MOISTURE_WET),
      // Sample Plots Forest Assessment & Kenya Coffee canopy audit
      "canopy_stratum-emergent.png" to ChoiceImageMedia(MIME_PNG, ICON_FOREST),
      "canopy_stratum-canopy.png" to ChoiceImageMedia(MIME_PNG, ICON_AGROFORESTRY),
      "canopy_stratum-mid_canopy.png" to ChoiceImageMedia(MIME_PNG, ICON_AGROFORESTRY),
      "canopy_stratum-understory.png" to ChoiceImageMedia(MIME_PNG, ICON_SAPLINGS),
      "canopy_stratum-ground_cover.png" to ChoiceImageMedia(MIME_PNG, ICON_PASTURE),
      "canopy_stratum-emergent_indigenous.png" to ChoiceImageMedia(MIME_PNG, ICON_FOREST),
      "canopy_stratum-mid_canopy_shade.png" to ChoiceImageMedia(MIME_PNG, ICON_AGROFORESTRY),
      "canopy_stratum-planted_grevillea.png" to ChoiceImageMedia(MIME_PNG, ICON_SAPLINGS),
      "canopy_stratum-full_sun_unshaded.png" to ChoiceImageMedia(MIME_PNG, ICON_MOISTURE_DRY),
      "alert_cause-no_disturbance.png" to ChoiceImageMedia(MIME_PNG, ICON_INTACT),
      "alert_cause-seasonal_pruning.png" to ChoiceImageMedia(MIME_PNG, ICON_AGROFORESTRY),
      "alert_cause-timber_felling.png" to ChoiceImageMedia(MIME_PNG, ICON_LOGGING),
      "alert_cause-charcoal_burn.png" to ChoiceImageMedia(MIME_PNG, ICON_FIRE),
      "alert_cause-agricultural_clearing.png" to ChoiceImageMedia(MIME_PNG, ICON_CROPLAND),
      "pest_or_disease-berry_borer.png" to ChoiceImageMedia(MIME_PNG, ICON_COFFEE),
      "pest_or_disease-coffee_leaf_rust.png" to ChoiceImageMedia(MIME_PNG, ICON_FIRE),
      "pest_or_disease-leaf_rust.png" to ChoiceImageMedia(MIME_PNG, ICON_FIRE),
      "pest_or_disease-healthy.png" to ChoiceImageMedia(MIME_PNG, ICON_FOREST),
      "pest_or_disease-stem_borer.png" to ChoiceImageMedia(MIME_PNG, ICON_LOGGING),
      "pest_or_disease-root_rot.png" to ChoiceImageMedia(MIME_PNG, ICON_INVASIVE),
      "effluent_treatment-eco_pulper_recirculation.png" to
        ChoiceImageMedia(MIME_PNG, ICON_MOISTURE_MOIST),
      "effluent_treatment-constructed_wetland.png" to ChoiceImageMedia(MIME_PNG, ICON_WETLAND),
      "effluent_treatment-vetiver_wetland.png" to ChoiceImageMedia(MIME_PNG, ICON_WETLAND),
      "effluent_treatment-settling_lagoon.png" to ChoiceImageMedia(MIME_PNG, ICON_MOISTURE_WET),
      "effluent_treatment-settling_pond.png" to ChoiceImageMedia(MIME_PNG, ICON_MOISTURE_WET),
      "effluent_treatment-untreated_discharge.png" to ChoiceImageMedia(MIME_PNG, ICON_FIRE),
      "effluent_treatment-untreated.png" to ChoiceImageMedia(MIME_PNG, ICON_FIRE),
    )

  private val runtimeByFileName = mutableMapOf<String, ChoiceImageMedia>()

  /** Registers an inline choice image under its `jr://images/<fileName>` URI or file name. */
  fun register(imageUri: String, mimeType: String, base64: String) {
    if (imageUri.isBlank() || base64.isBlank()) return
    val fileName = imageUri.substringAfterLast('/').trim()
    if (fileName.isNotEmpty()) {
      runtimeByFileName[fileName] = ChoiceImageMedia(mimeType.ifBlank { MIME_PNG }, base64)
    }
  }

  /** Looks up the [ChoiceImageMedia] for a `jr://images/<fileName>` URI, or `null` if unknown. */
  fun lookup(imageUri: String?): ChoiceImageMedia? {
    val clean = imageUri?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val fileName = clean.substringAfterLast('/')
    return runtimeByFileName[fileName] ?: builtInByFileName[fileName]
  }

  /** Decodes the choice image for [imageUri] into an [ImageBitmap], or `null` if unavailable. */
  @OptIn(ExperimentalResourceApi::class, ExperimentalEncodingApi::class)
  @Composable
  fun rememberChoiceImageBitmap(imageUri: String?): ImageBitmap? {
    val media = lookup(imageUri) ?: return null
    return remember(media.base64) {
      runCatching { Base64.decode(media.base64).decodeToImageBitmap() }.getOrNull()
    }
  }

  private val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

  /** Parses a `#RRGGBB` choice color string into a Compose [Color], or `null` if absent/invalid. */
  fun parseChoiceColor(hex: String?): Color? {
    val clean = hex?.trim()?.takeIf { HEX_COLOR_REGEX.matches(it) } ?: return null
    val rgb = clean.substring(1).toLongOrNull(16) ?: return null
    return Color(0xFF000000L or rgb)
  }
}
