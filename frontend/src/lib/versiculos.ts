// Lista curada de 60 versículos. O "versículo do dia" é escolhido de forma determinística
// pela data (dia-do-ano % 60), então é estável durante o dia e roda diariamente.
// Sem API externa: zero dependência, latência ou ponto de falha.

export interface Versiculo {
  texto: string
  ref: string
}

export const VERSICULOS: Versiculo[] = [
  { texto: 'O Senhor é o meu pastor; nada me faltará.', ref: 'Salmos 23:1' },
  { texto: 'Tudo posso naquele que me fortalece.', ref: 'Filipenses 4:13' },
  { texto: 'O Senhor é a minha luz e a minha salvação; a quem temerei?', ref: 'Salmos 27:1' },
  { texto: 'Entrega o teu caminho ao Senhor; confia nele, e ele o fará.', ref: 'Salmos 37:5' },
  { texto: 'Deus é o nosso refúgio e fortaleza, socorro bem presente na angústia.', ref: 'Salmos 46:1' },
  { texto: 'Aquietai-vos e sabei que eu sou Deus.', ref: 'Salmos 46:10' },
  { texto: 'Deleita-te no Senhor, e ele te concederá o que deseja o teu coração.', ref: 'Salmos 37:4' },
  { texto: 'O choro pode durar uma noite, mas a alegria vem pela manhã.', ref: 'Salmos 30:5' },
  { texto: 'Lâmpada para os meus pés é a tua palavra e luz para o meu caminho.', ref: 'Salmos 119:105' },
  { texto: 'Confia no Senhor de todo o teu coração e não te estribes no teu próprio entendimento.', ref: 'Provérbios 3:5' },
  { texto: 'Reconhece-o em todos os teus caminhos, e ele endireitará as tuas veredas.', ref: 'Provérbios 3:6' },
  { texto: 'O temor do Senhor é o princípio da sabedoria.', ref: 'Provérbios 9:10' },
  { texto: 'A resposta branda desvia o furor.', ref: 'Provérbios 15:1' },
  { texto: 'Os que esperam no Senhor renovarão as suas forças.', ref: 'Isaías 40:31' },
  { texto: 'Não temas, porque eu sou contigo; não te assombres, porque eu sou o teu Deus.', ref: 'Isaías 41:10' },
  { texto: 'Ainda que passes pelas águas, estarei contigo.', ref: 'Isaías 43:2' },
  { texto: 'Porque sou eu que conheço os planos que tenho para vós, planos de paz e não de mal.', ref: 'Jeremias 29:11' },
  { texto: 'As misericórdias do Senhor se renovam cada manhã; grande é a sua fidelidade.', ref: 'Lamentações 3:22-23' },
  { texto: 'Buscai primeiro o Reino de Deus, e a sua justiça, e todas estas coisas vos serão acrescentadas.', ref: 'Mateus 6:33' },
  { texto: 'Vinde a mim, todos os que estais cansados e sobrecarregados, e eu vos aliviarei.', ref: 'Mateus 11:28' },
  { texto: 'Porque, onde estiverem dois ou três reunidos em meu nome, aí estou eu no meio deles.', ref: 'Mateus 18:20' },
  { texto: 'Para Deus tudo é possível.', ref: 'Mateus 19:26' },
  { texto: 'Amarás o teu próximo como a ti mesmo.', ref: 'Marcos 12:31' },
  { texto: 'Porque para Deus nada é impossível.', ref: 'Lucas 1:37' },
  { texto: 'Dai, e ser-vos-á dado.', ref: 'Lucas 6:38' },
  { texto: 'Porque Deus amou o mundo de tal maneira que deu o seu Filho unigênito.', ref: 'João 3:16' },
  { texto: 'Eu sou o caminho, e a verdade, e a vida.', ref: 'João 14:6' },
  { texto: 'A paz vos deixo, a minha paz vos dou.', ref: 'João 14:27' },
  { texto: 'Eu vim para que tenham vida e a tenham em abundância.', ref: 'João 10:10' },
  { texto: 'Todas as coisas contribuem juntamente para o bem daqueles que amam a Deus.', ref: 'Romanos 8:28' },
  { texto: 'Se Deus é por nós, quem será contra nós?', ref: 'Romanos 8:31' },
  { texto: 'Nem a morte, nem a vida poderá nos separar do amor de Deus.', ref: 'Romanos 8:38-39' },
  { texto: 'Alegrai-vos na esperança, sede pacientes na tribulação, perseverai na oração.', ref: 'Romanos 12:12' },
  { texto: 'O amor é paciente, é benigno; o amor não arde em ciúmes.', ref: '1 Coríntios 13:4' },
  { texto: 'Agora, pois, permanecem a fé, a esperança e o amor; mas o maior destes é o amor.', ref: '1 Coríntios 13:13' },
  { texto: 'Portanto, se alguém está em Cristo, é nova criatura.', ref: '2 Coríntios 5:17' },
  { texto: 'A minha graça te basta, porque o poder se aperfeiçoa na fraqueza.', ref: '2 Coríntios 12:9' },
  { texto: 'Já estou crucificado com Cristo; e vivo, não mais eu, mas Cristo vive em mim.', ref: 'Gálatas 2:20' },
  { texto: 'Mas o fruto do Espírito é: amor, alegria, paz, longanimidade, benignidade.', ref: 'Gálatas 5:22' },
  { texto: 'E não nos cansemos de fazer o bem.', ref: 'Gálatas 6:9' },
  { texto: 'Àquele que é poderoso para fazer tudo muito mais abundantemente além daquilo que pedimos.', ref: 'Efésios 3:20' },
  { texto: 'Não andeis ansiosos por coisa alguma.', ref: 'Filipenses 4:6' },
  { texto: 'E a paz de Deus, que excede todo o entendimento, guardará os vossos corações.', ref: 'Filipenses 4:7' },
  { texto: 'O meu Deus, segundo as suas riquezas, suprirá todas as vossas necessidades.', ref: 'Filipenses 4:19' },
  { texto: 'Tudo quanto fizerdes, fazei-o de todo o coração, como para o Senhor.', ref: 'Colossenses 3:23' },
  { texto: 'Regozijai-vos sempre. Orai sem cessar. Em tudo dai graças.', ref: '1 Tessalonicenses 5:16-18' },
  { texto: 'Porque Deus não nos deu espírito de covardia, mas de poder, de amor e de moderação.', ref: '2 Timóteo 1:7' },
  { texto: 'Ora, a fé é a certeza de coisas que se esperam, a convicção de fatos que se não veem.', ref: 'Hebreus 11:1' },
  { texto: 'Jesus Cristo é o mesmo, ontem, e hoje, e eternamente.', ref: 'Hebreus 13:8' },
  { texto: 'Toda boa dádiva e todo dom perfeito vêm do alto.', ref: 'Tiago 1:17' },
  { texto: 'Chegai-vos a Deus, e ele se chegará a vós.', ref: 'Tiago 4:8' },
  { texto: 'Lançando sobre ele toda a vossa ansiedade, porque ele tem cuidado de vós.', ref: '1 Pedro 5:7' },
  { texto: 'Nós amamos porque ele nos amou primeiro.', ref: '1 João 4:19' },
  { texto: 'No amor não há medo; antes, o perfeito amor lança fora o medo.', ref: '1 João 4:18' },
  { texto: 'Eis que estou à porta e bato; se alguém ouvir a minha voz e abrir a porta, entrarei.', ref: 'Apocalipse 3:20' },
  { texto: 'Bem-aventurados os que têm fome e sede de justiça, porque serão fartos.', ref: 'Mateus 5:6' },
  { texto: 'Vós sois a luz do mundo.', ref: 'Mateus 5:14' },
  { texto: 'Sê forte e corajoso; não temas, nem te espantes, porque o Senhor teu Deus é contigo.', ref: 'Josué 1:9' },
  { texto: 'O Senhor pelejará por vós, e vós vos calareis.', ref: 'Êxodo 14:14' },
  { texto: 'Provai e vede que o Senhor é bom; bem-aventurado o homem que nele confia.', ref: 'Salmos 34:8' },
]

/** Versículo do dia — determinístico pela data (mesmo versículo o dia inteiro, roda diariamente). */
export function versiculoDoDia(data: Date = new Date()): Versiculo {
  const inicioAno = new Date(data.getFullYear(), 0, 0)
  const diff = data.getTime() - inicioAno.getTime()
  const diaDoAno = Math.floor(diff / 86_400_000)
  return VERSICULOS[diaDoAno % VERSICULOS.length]
}
